package io.coti.basenode.services;

import com.google.common.collect.Sets;
import io.coti.basenode.data.*;
import io.coti.basenode.http.data.TransactionStatus;
import io.coti.basenode.model.TransactionIndexes;
import io.coti.basenode.model.Transactions;
import io.coti.basenode.services.interfaces.IBalanceService;
import io.coti.basenode.services.interfaces.IConfirmationService;
import io.coti.basenode.services.interfaces.ITransactionHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class BaseNodeConfirmationService implements IConfirmationService {

    @Autowired
    private IBalanceService balanceService;
    @Autowired
    private ITransactionHelper transactionHelper;
    @Autowired
    private TransactionIndexService transactionIndexService;
    @Autowired
    private TransactionIndexes transactionIndexes;
    @Autowired
    private Transactions transactions;
    private BlockingQueue<ConfirmationData> confirmationQueue;
    private Map<Long, DspConsensusResult> waitingDspConsensusResults = new ConcurrentHashMap<>();
    private Map<Long, TransactionData> waitingMissingTransactionIndexes = new ConcurrentHashMap<>();
    private Map<Hash, LinkedHashSet<Hash>> waitingDSPConsensusTransactionsByAddress = new ConcurrentHashMap<>();
    private AtomicLong totalConsensus = new AtomicLong(0);
    private AtomicLong trustChainConfirmed = new AtomicLong(0);
    private AtomicLong dspConfirmed = new AtomicLong(0);
    private AtomicLong dspRejected = new AtomicLong(0);
    private Thread confirmedTransactionsThread;

    public void init() {
        confirmationQueue = new LinkedBlockingQueue<>();
        confirmedTransactionsThread = new Thread(this::updateConfirmedTransactions);
        confirmedTransactionsThread.start();

        log.info("{} is up", this.getClass().getSimpleName());
    }

    @Override
    public void setLastDspConfirmationIndex(AtomicLong maxTransactionIndex) {
        log.info("Started to set last dsp confirmation index");
        byte[] accumulatedHash = "GENESIS".getBytes();
        TransactionIndexData transactionIndexData = new TransactionIndexData(new Hash(-1), -1, "GENESIS".getBytes());
        TransactionIndexData nextTransactionIndexData;
        try {
            for (long i = 0; i <= maxTransactionIndex.get(); i++) {
                nextTransactionIndexData = transactionIndexes.getByHash(new Hash(i));
                if (nextTransactionIndexData == null) {
                    log.error("Null transaction index data found for index {}", i);
                    return;
                }

                TransactionData transactionData = transactions.getByHash(nextTransactionIndexData.getTransactionHash());
                if (transactionData == null) {
                    log.error("Null transaction data found for index {}", i);
                    return;
                }
                if (transactionData.getDspConsensusResult() == null) {
                    log.error("Null dsp consensus result found for index {} and transaction {}", i, transactionData.getHash());
                    return;
                }
                accumulatedHash = transactionIndexService.getAccumulatedHash(accumulatedHash, transactionData.getHash(), transactionData.getDspConsensusResult().getIndex());
                if (!Arrays.equals(accumulatedHash, nextTransactionIndexData.getAccumulatedHash())) {
                    log.error("Incorrect accumulated hash");
                    return;
                }
                dspConfirmed.incrementAndGet();
                if (transactionData.isTrustChainConsensus()) {
                    totalConsensus.incrementAndGet();
                    transactionData.getBaseTransactions().forEach(baseTransactionData ->
                            balanceService.updateBalance(baseTransactionData.getAddressHash(), baseTransactionData.getAmount())
                    );
                }
                transactionIndexData = nextTransactionIndexData;
            }
        } finally {
            transactionIndexService.setLastTransactionIndexData(transactionIndexData);
            log.info("Finished to set last dsp confirmation index: {}", transactionIndexData.getIndex());
        }
    }

    private void updateConfirmedTransactions() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                ConfirmationData confirmationData = confirmationQueue.take();
                updateConfirmedTransactionHandler(confirmationData);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        LinkedList<ConfirmationData> remainingConfirmedTransactions = new LinkedList<>();
        confirmationQueue.drainTo(remainingConfirmedTransactions);
        if (!remainingConfirmedTransactions.isEmpty()) {
            log.info("Please wait to process {} remaining confirmed transaction(s)", remainingConfirmedTransactions.size());
            remainingConfirmedTransactions.forEach(this::updateConfirmedTransactionHandler);
        }
    }

    private void updateConfirmedTransactionHandler(ConfirmationData confirmationData) {
        transactions.lockAndGetByHash(confirmationData.getHash(), transactionData -> {
            Boolean transactionDataOriginallyValid = transactionData.isValid();
            if (confirmationData instanceof TccInfo) {
                transactionData.setTrustChainConsensus(true);
                transactionData.setTrustChainConsensusTime(((TccInfo) confirmationData).getTrustChainConsensusTime());
                transactionData.setTrustChainTrustScore(((TccInfo) confirmationData).getTrustChainTrustScore());
                trustChainConfirmed.incrementAndGet();
            } else if (confirmationData instanceof DspConsensusResult) {
                if (transactionData.getDspConsensusResult() != null) {
                    log.error("Dsp consensus result already executed for transaction {}", transactionData.getHash());
                    return;
                }
                transactionData.setDspConsensusResult((DspConsensusResult) confirmationData);
                Optional<Boolean> optionalInsertNewTransactionIndex = insertNewTransactionIndex(transactionData);

                if ((optionalInsertNewTransactionIndex.isPresent() && Boolean.FALSE.equals(optionalInsertNewTransactionIndex.get())) ||
                        !isNeededResourceAllocated(transactionData, transactionDataOriginallyValid)) {
                    return;
                }
                if (transactionHelper.isDspConfirmed(transactionData)) {
                    processDSPConfirmedTransaction(transactionData);
                    continueHandleDSPConfirmedTransaction(transactionData);
                    dspConfirmed.incrementAndGet();
                }
                if (transactionHelper.isDspRejected(transactionData)) {
                    processDSPRejectedTransaction(transactionData, transactionDataOriginallyValid);
                    continueHandleDSPRejectedTransaction(transactionData);
                    dspRejected.incrementAndGet();
                }
            }
            if (transactionHelper.isConfirmed(transactionData)) {
                processConfirmedTransaction(transactionData);
            }
            transactions.put(transactionData);
        });
    }

    private boolean isNeededResourceAllocated(TransactionData transactionData, Boolean transactionDataOriginallyValid) {
        boolean neededResourceAllocated = false;
        if (transactionDataOriginallyValid || transactionHelper.isDspRejected(transactionData)) {
            neededResourceAllocated = true;
        } else {
            if (transactionHelper.isDspConfirmed(transactionData)) {
                if (balanceService.checkBalancesAndAddToPreBalance(transactionData.getBaseTransactions())) {
                    transactionData.setValid(true);
                    transactionData.getInputBaseTransactions().forEach(inputBaseTransactionData ->
                            waitingDSPConsensusTransactionsByAddress.getOrDefault(inputBaseTransactionData.getAddressHash(), new LinkedHashSet<>()).remove(transactionData.getHash()));
                    neededResourceAllocated = true;
                } else {
                    transactionData.getInputBaseTransactions().forEach(inputBaseTransactionData -> {
                        Hash addressHash = inputBaseTransactionData.getAddressHash();
                        waitingDSPConsensusTransactionsByAddress.putIfAbsent(addressHash, new LinkedHashSet<>());
                        waitingDSPConsensusTransactionsByAddress.get(addressHash).add(transactionData.getHash());
                    });
                }
            }
        }
        return neededResourceAllocated;
    }

    private void processDSPRejectedTransaction(TransactionData transactionData, Boolean transactionDataOriginallyValid) {
        if (transactionDataOriginallyValid) {
            balanceService.rollbackBaseTransactions(transactionData);
            LinkedHashSet<Hash> dspResultConsensusHashes = Sets.newLinkedHashSet();
            transactionData.getInputBaseTransactions().forEach(inputBaseTransactionData -> {
                waitingDSPConsensusTransactionsByAddress.getOrDefault(inputBaseTransactionData.getAddressHash(), new LinkedHashSet<>()).remove(transactionData.getHash());
                LinkedHashSet<Hash> remainingTransactionHashes = waitingDSPConsensusTransactionsByAddress.get(inputBaseTransactionData.getAddressHash());
                if (!remainingTransactionHashes.isEmpty()) {
                    dspResultConsensusHashes.addAll(remainingTransactionHashes);
                }
            });
            dspResultConsensusHashes.forEach(dspResultConsensusHash -> {
                TransactionData waitingTransactionData = transactions.getByHash(dspResultConsensusHash);
                updateConfirmedTransactionHandler(waitingTransactionData.getDspConsensusResult());
                setDspc(waitingTransactionData.getDspConsensusResult());
            });
        }
    }

    private void processDSPConfirmedTransaction(TransactionData transactionData) {
        // Consider adding logic
    }

    private void processConfirmedTransaction(TransactionData transactionData) {
        processTransaction(transactionData);
        totalConsensus.incrementAndGet();
        transactionData.getBaseTransactions().forEach(baseTransactionData -> {
            balanceService.updateBalance(baseTransactionData.getAddressHash(), baseTransactionData.getAmount());
            balanceService.continueHandleBalanceChanges(baseTransactionData.getAddressHash());
        });
        continueHandleAddressHistoryChanges(transactionData, TransactionStatus.CONFIRMED);
    }

    private void processTransaction(TransactionData transactionData) {
        Instant trustChainConsensusTime = transactionData.getTrustChainConsensusTime();
        Instant dspConsensusTime = transactionData.getDspConsensusResult().getIndexingTime();
        Instant transactionConsensusUpdateTime = trustChainConsensusTime.isAfter(dspConsensusTime) ? trustChainConsensusTime : dspConsensusTime;
        transactionData.setTransactionConsensusUpdateTime(transactionConsensusUpdateTime);
    }

    protected Optional<Boolean> insertNewTransactionIndex(TransactionData transactionData) {
        Optional<Boolean> optionalInsertNewTransactionIndex = transactionIndexService.insertNewTransactionIndex(transactionData);
        if (!optionalInsertNewTransactionIndex.isPresent()) {
            return optionalInsertNewTransactionIndex;
        }
        Boolean isNewTransactionIndexInserted = optionalInsertNewTransactionIndex.get();
        DspConsensusResult dspConsensusResult = transactionData.getDspConsensusResult();
        if (Boolean.FALSE.equals(isNewTransactionIndexInserted)) {
            waitingDspConsensusResults.put(dspConsensusResult.getIndex(), dspConsensusResult);
        } else {
            long index = dspConsensusResult.getIndex() + 1;
            while (waitingDspConsensusResults.containsKey(index)) {
                setDspc(waitingDspConsensusResults.get(index));
                waitingDspConsensusResults.remove(index);
                index++;
            }
        }
        return optionalInsertNewTransactionIndex;
    }

    protected void continueHandleDSPConfirmedTransaction(TransactionData transactionData) {
        // implemented by the sub classes
    }

    protected void continueHandleDSPRejectedTransaction(TransactionData transactionData) {
        // implemented by the sub classes
    }

    protected void continueHandleAddressHistoryChanges(TransactionData transactionData, TransactionStatus confirmed) {
        // implemented by the sub classes
    }

    @Override
    public void insertSavedTransaction(TransactionData transactionData, AtomicLong maxTransactionIndex) {
        boolean isDspConfirmed = transactionHelper.isDspConfirmed(transactionData) && transactionHelper.hasDspResultAndIndexed(transactionData);
        transactionData.getBaseTransactions().forEach(baseTransactionData ->
                balanceService.updatePreBalance(baseTransactionData.getAddressHash(), baseTransactionData.getAmount())
        );

        if (!isDspConfirmed) {
            transactionHelper.addNoneIndexedTransaction(transactionData);
        }
        if (transactionData.getDspConsensusResult() != null) {
            maxTransactionIndex.set(Math.max(maxTransactionIndex.get(), transactionData.getDspConsensusResult().getIndex()));
        }

        if (transactionData.isTrustChainConsensus()) {
            trustChainConfirmed.incrementAndGet();
        }
    }

    @Override
    public void insertMissingTransaction(TransactionData transactionData) {
        transactionData.getBaseTransactions().forEach(baseTransactionData -> balanceService.updatePreBalance(baseTransactionData.getAddressHash(), baseTransactionData.getAmount()));
        if (transactionData.isTrustChainConsensus()) {
            trustChainConfirmed.incrementAndGet();
        }
        insertMissingDspConsensus(transactionData);
    }

    @Override
    public void insertMissingConfirmation(TransactionData transactionData, Set<Hash> trustChainUnconfirmedExistingTransactionHashes) {
        if (trustChainUnconfirmedExistingTransactionHashes.contains(transactionData.getHash()) && transactionData.isTrustChainConsensus()) {
            trustChainConfirmed.incrementAndGet();
        }
        insertMissingDspConsensus(transactionData);
    }

    private void insertMissingDspConsensus(TransactionData transactionData) {
        if (!transactionHelper.hasDspResultAndIndexed(transactionData)) {
            transactionHelper.addNoneIndexedTransaction(transactionData);
        }
        if (transactionData.getDspConsensusResult() != null) {
            insertMissingTransactionIndex(transactionData);
        }
    }

    private void insertMissingTransactionIndex(TransactionData transactionData) {
        Optional<Boolean> optionalInsertNewTransactionIndex = transactionIndexService.insertNewTransactionIndex(transactionData);
        if (!optionalInsertNewTransactionIndex.isPresent()) {
            return;
        }
        Boolean isNewTransactionIndexInserted = optionalInsertNewTransactionIndex.get();
        DspConsensusResult dspConsensusResult = transactionData.getDspConsensusResult();
        if (Boolean.FALSE.equals(isNewTransactionIndexInserted)) {
            waitingMissingTransactionIndexes.put(dspConsensusResult.getIndex(), transactionData);
        } else {
            processMissingDspConsensusTransaction(transactionData);
            long index = dspConsensusResult.getIndex() + 1;
            while (waitingMissingTransactionIndexes.containsKey(index)) {
                TransactionData waitingMissingTransactionData = waitingMissingTransactionIndexes.get(index);
                transactionIndexService.insertNewTransactionIndex(waitingMissingTransactionData);
                processMissingDspConsensusTransaction(waitingMissingTransactionData);
                waitingMissingTransactionIndexes.remove(index);
                index++;
            }
        }
    }

    private void processMissingDspConsensusTransaction(TransactionData transactionData) {
        continueHandleDSPConfirmedTransaction(transactionData);
        if (transactionHelper.isDspConfirmed(transactionData)) {
            dspConfirmed.incrementAndGet();
        } else {
            dspRejected.incrementAndGet();
        }
        if (transactionData.isTrustChainConsensus()) {
            if (Boolean.TRUE.equals(transactionData.isValid())) {
                transactionData.getBaseTransactions().forEach(baseTransactionData -> balanceService.updateBalance(baseTransactionData.getAddressHash(), baseTransactionData.getAmount()));
            }
            totalConsensus.incrementAndGet();
        }
    }

    @Override
    public void setTccToTrue(TccInfo tccInfo) {
        try {
            confirmationQueue.put(tccInfo);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void setDspc(DspConsensusResult dspConsensusResult) {
        try {
            confirmationQueue.put(dspConsensusResult);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public long getTotalConsensus() {
        return totalConsensus.get();
    }

    @Override
    public long getTrustChainConfirmed() {
        return trustChainConfirmed.get();
    }

    @Override
    public long getDspConfirmed() {
        return dspConfirmed.get();
    }

    @Override
    public long getDspRejected() {
        return dspRejected.get();
    }

    public void shutdown() {
        log.info("Shutting down {}", this.getClass().getSimpleName());
        confirmedTransactionsThread.interrupt();
        try {
            confirmedTransactionsThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted shutdown {}", this.getClass().getSimpleName());
        }

    }

}
