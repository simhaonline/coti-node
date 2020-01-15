package io.coti.nodemanager.services;

import io.coti.basenode.communication.interfaces.IPropagationPublisher;
import io.coti.basenode.data.*;
import io.coti.basenode.services.interfaces.*;
import io.coti.nodemanager.data.NetworkNodeStatus;
import io.coti.nodemanager.database.RocksDBConnector;
import io.coti.nodemanager.model.ActiveNodes;
import io.coti.nodemanager.model.NodeDailyActivities;
import io.coti.nodemanager.model.NodeHistory;
import io.coti.nodemanager.model.StakingNodes;
import io.coti.nodemanager.services.interfaces.IHealthCheckService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@ContextConfiguration(classes = {NodeManagementService.class, RocksDBConnector.class, NodeHistory.class, NodeDailyActivities.class, InitializationService.class, NetworkHistoryService.class})
@TestPropertySource(locations = "classpath:properties")
@RunWith(SpringRunner.class)
@SpringBootTest()
public class NodeManagementServiceDoNotTakeItToDevTest {
    @Autowired
    private NodeManagementService nodeManagementService;
    @Autowired
    private InitializationService initializationService;
    @Autowired
    private NodeHistory nodeHistory;
    @Autowired
    private NodeDailyActivities nodeDailyActivities;
    @MockBean
    private ActiveNodes activeNodes;
    @MockBean
    private IPropagationPublisher propagationPublisher;
    @MockBean
    private INetworkService networkService;
    @MockBean
    private StakingService stakingService;
    @MockBean
    private StakingNodes stakingNodes;
    @MockBean
    private IAwsService awsService;
    @MockBean
    private IDBRecoveryService dbRecoveryService;
    @MockBean
    private IShutDownService shutDownService;
    @MockBean
    private IHealthCheckService healthCheckService;
    @MockBean
    private ICommunicationService communicationService;
    @MockBean
    private ApplicationContext applicationContext;
    @MockBean
    private BuildProperties buildProperties;
    @Autowired
    private NetworkHistoryService networkHistoryService;

    private static final Hash fakeNode1 = new Hash("76d7f333480680c06df7d5c3cc17ffe2dc052597ae857285e57da249f4df344cf3e112739eca2aea63437f9e9819fac909ab93801b99853c779d8b6f5dcafb74");
    private static final Hash fakeNode2 = new Hash("a2d27c3248e3530c55ca0941fd0fe5f419efcb6f923e54fe83ec5024040f86d107c6882f6a2435408964c2e9f522579248c8a983a2761a03ba253e7ca7898e53");
    private static final Hash fakeNode3 = new Hash("0aa389aa3d8b31ecc5b2fa9164a0a2f52fb59165730de4527441b0278e5e47e51e3e1e69cf24a1a0bb58a53b262c185c4400f0d2f89b469c9498b6ed517b7398");
    private static final Hash fakeNode4 = new Hash("e70a7477209fa59b3e866b33184ae47e5bed0d202c7214a4a93fd2592b11c3b567f2e85d28f3fc415401bb5a6b8be9eae5e77aa18d7e042c33ba91396d3cd970");
    private static final Hash fakeNode5 = new Hash("5a4a7a8b72384bd6310135fdd939d1b105aec81a6ad72d745e5636770690a17c31eb6a775860b65b6211ec27d0690802032123a7f34f3acb68ed5d66366cd003");
    private static final Hash fakeNode6 = new Hash("cd10ad2f479647dab74c0017958399a9ce87a56672bfd36739c70c4ddd2b2b5f451ff5deb10c86b745fcfa08dcb3ff1f331124bca608f5eab247ad1ec6e18281");

    @Test
    public void addNodeHistoryTest() {

        NetworkNodeStatus nodeStatus;
        Instant eventDateTime;

        NetworkNodeData networkNodeData1 = new NetworkNodeData();
        networkNodeData1.setHash(fakeNode1);
        networkNodeData1.setNodeType(NodeType.FullNode);
        networkNodeData1.setAddress("test");
        networkNodeData1.setHttpPort("000");
        networkNodeData1.setPropagationPort("000");
        networkNodeData1.setReceivingPort("000");
        networkNodeData1.setNetworkType(NetworkType.TestNet);
        networkNodeData1.setTrustScore(17.0);
        networkNodeData1.setWebServerUrl("test");
        networkNodeData1.setFeeData(new FeeData(BigDecimal.valueOf(0.1), BigDecimal.valueOf(0.1), BigDecimal.valueOf(0.1)));
        networkNodeData1.setNodeSignature(new SignatureData("test", "test"));
        networkNodeData1.setNodeRegistrationData(new NodeRegistrationData());

        networkNodeData1.getNodeRegistrationData().setNodeHash(fakeNode1);
        networkNodeData1.getNodeRegistrationData().setNodeType(NodeType.FullNode.toString());
        networkNodeData1.getNodeRegistrationData().setNetworkType(NetworkType.TestNet.toString());
        networkNodeData1.getNodeRegistrationData().setCreationTime(Instant.now());
        networkNodeData1.getNodeRegistrationData().setRegistrarHash(new Hash("00"));
        networkNodeData1.getNodeRegistrationData().setRegistrarSignature(new SignatureData("test", "test"));

        eventDateTime = LocalDateTime.of(2019, 11, 27, 10, 0, 0).toInstant(ZoneOffset.UTC);
        nodeStatus = NetworkNodeStatus.ACTIVE;
        nodeManagementService.addNodeHistory(networkNodeData1, nodeStatus, eventDateTime);
        eventDateTime = LocalDateTime.of(2019, 12, 2, 11, 5, 27).toInstant(ZoneOffset.UTC);
        nodeManagementService.addNodeHistory(networkNodeData1, nodeStatus, eventDateTime);
        eventDateTime = LocalDateTime.of(2019, 12, 3, 15, 34, 32).toInstant(ZoneOffset.UTC);
        nodeManagementService.addNodeHistory(networkNodeData1, nodeStatus, eventDateTime);
        eventDateTime = LocalDateTime.of(2019, 12, 11, 1, 52, 11).toInstant(ZoneOffset.UTC);
        nodeStatus = NetworkNodeStatus.INACTIVE;
        nodeManagementService.addNodeHistory(networkNodeData1, nodeStatus, eventDateTime);

        eventDateTime = LocalDateTime.of(2019, 12, 15, 4, 41, 33).toInstant(ZoneOffset.UTC);
        nodeStatus = NetworkNodeStatus.ACTIVE;
        nodeManagementService.addNodeHistory(networkNodeData1, nodeStatus, eventDateTime);
        eventDateTime = LocalDateTime.of(2019, 12, 16, 7, 22, 22).toInstant(ZoneOffset.UTC);
        nodeManagementService.addNodeHistory(networkNodeData1, nodeStatus, eventDateTime);

        eventDateTime = LocalDateTime.of(2019, 12, 25, 18, 16, 3).toInstant(ZoneOffset.UTC);
        nodeStatus = NetworkNodeStatus.INACTIVE;
        nodeManagementService.addNodeHistory(networkNodeData1, nodeStatus, eventDateTime);

        eventDateTime = LocalDateTime.of(2019, 12, 30, 3, 28, 1).toInstant(ZoneOffset.UTC);
        nodeStatus = NetworkNodeStatus.ACTIVE;
        nodeManagementService.addNodeHistory(networkNodeData1, nodeStatus, eventDateTime);

        log.info("fakeNode1 finished");

        NetworkNodeData networkNodeData2 = new NetworkNodeData();
        networkNodeData2.setHash(fakeNode2);
        networkNodeData2.setNodeType(NodeType.FullNode);
        networkNodeData2.setAddress("test");
        networkNodeData2.setHttpPort("000");
        networkNodeData2.setPropagationPort("000");
        networkNodeData2.setReceivingPort("000");
        networkNodeData2.setNetworkType(NetworkType.TestNet);
        networkNodeData2.setTrustScore(77.0);
        networkNodeData2.setWebServerUrl("test");
        networkNodeData2.setFeeData(new FeeData(BigDecimal.valueOf(0.2), BigDecimal.valueOf(0.2), BigDecimal.valueOf(0.2)));
        networkNodeData2.setNodeSignature(new SignatureData("test", "test"));
        networkNodeData2.setNodeRegistrationData(new NodeRegistrationData());

        networkNodeData2.getNodeRegistrationData().setNodeHash(fakeNode2);
        networkNodeData2.getNodeRegistrationData().setNodeType(NodeType.FullNode.toString());
        networkNodeData2.getNodeRegistrationData().setNetworkType(NetworkType.TestNet.toString());
        networkNodeData2.getNodeRegistrationData().setCreationTime(Instant.now());
        networkNodeData2.getNodeRegistrationData().setRegistrarHash(new Hash("00"));
        networkNodeData2.getNodeRegistrationData().setRegistrarSignature(new SignatureData("test", "test"));

        eventDateTime = LocalDateTime.of(2019, 12, 1, 4, 41, 33).toInstant(ZoneOffset.UTC);
        nodeStatus = NetworkNodeStatus.ACTIVE;
        nodeManagementService.addNodeHistory(networkNodeData2, nodeStatus, eventDateTime);

        eventDateTime = LocalDateTime.of(2019, 12, 5, 18, 16, 3).toInstant(ZoneOffset.UTC);
        nodeStatus = NetworkNodeStatus.INACTIVE;
        nodeManagementService.addNodeHistory(networkNodeData2, nodeStatus, eventDateTime);

        eventDateTime = LocalDateTime.of(2019, 12, 10, 4, 41, 33).toInstant(ZoneOffset.UTC);
        nodeStatus = NetworkNodeStatus.ACTIVE;
        nodeManagementService.addNodeHistory(networkNodeData2, nodeStatus, eventDateTime);

        eventDateTime = LocalDateTime.of(2019, 12, 10, 6, 41, 33).toInstant(ZoneOffset.UTC);
        nodeStatus = NetworkNodeStatus.INACTIVE;
        nodeManagementService.addNodeHistory(networkNodeData2, nodeStatus, eventDateTime);

        eventDateTime = LocalDateTime.of(2019, 12, 10, 10, 41, 33).toInstant(ZoneOffset.UTC);
        nodeStatus = NetworkNodeStatus.ACTIVE;
        nodeManagementService.addNodeHistory(networkNodeData2, nodeStatus, eventDateTime);

        eventDateTime = LocalDateTime.of(2019, 12, 15, 18, 16, 3).toInstant(ZoneOffset.UTC);
        nodeStatus = NetworkNodeStatus.INACTIVE;
        nodeManagementService.addNodeHistory(networkNodeData2, nodeStatus, eventDateTime);

        eventDateTime = LocalDateTime.of(2019, 12, 20, 4, 41, 33).toInstant(ZoneOffset.UTC);
        nodeStatus = NetworkNodeStatus.ACTIVE;
        nodeManagementService.addNodeHistory(networkNodeData2, nodeStatus, eventDateTime);

        eventDateTime = LocalDateTime.of(2019, 12, 25, 18, 16, 3).toInstant(ZoneOffset.UTC);
        nodeStatus = NetworkNodeStatus.INACTIVE;
        nodeManagementService.addNodeHistory(networkNodeData2, nodeStatus, eventDateTime);

        log.info("fakeNode2 finished");

        NetworkNodeData networkNodeData3 = new NetworkNodeData();
        networkNodeData3.setHash(fakeNode3);
        networkNodeData3.setNodeType(NodeType.FullNode);
        networkNodeData3.setAddress("test");
        networkNodeData3.setHttpPort("000");
        networkNodeData3.setPropagationPort("000");
        networkNodeData3.setReceivingPort("000");
        networkNodeData3.setNetworkType(NetworkType.TestNet);
        networkNodeData3.setTrustScore(37.0);
        networkNodeData3.setWebServerUrl("test");
        networkNodeData3.setFeeData(new FeeData(BigDecimal.valueOf(0.3), BigDecimal.valueOf(0.3), BigDecimal.valueOf(0.3)));
        networkNodeData3.setNodeSignature(new SignatureData("test", "test"));
        networkNodeData3.setNodeRegistrationData(new NodeRegistrationData());

        networkNodeData3.getNodeRegistrationData().setNodeHash(fakeNode3);
        networkNodeData3.getNodeRegistrationData().setNodeType(NodeType.FullNode.toString());
        networkNodeData3.getNodeRegistrationData().setNetworkType(NetworkType.TestNet.toString());
        networkNodeData3.getNodeRegistrationData().setCreationTime(Instant.now());
        networkNodeData3.getNodeRegistrationData().setRegistrarHash(new Hash("00"));
        networkNodeData3.getNodeRegistrationData().setRegistrarSignature(new SignatureData("test", "test"));

        LocalDateTime startDateTime = LocalDateTime.of(2019, 11, 25, 0, 0, 0);
        Instant localDateTime;

        for (int i = 0; i < 40; i++) {
            for (int j = 0; j < 24; j++) {
                for (int k = 0; k < 5; k++) {  // or 60
                    nodeStatus = NetworkNodeStatus.ACTIVE;
                    localDateTime = LocalDateTime.of(startDateTime.getYear(), startDateTime.getMonth(), startDateTime.getDayOfMonth(), j, k, 0).toInstant(ZoneOffset.UTC);
                    nodeManagementService.addNodeHistory(networkNodeData3, nodeStatus, localDateTime);

                    nodeStatus = NetworkNodeStatus.INACTIVE;
                    localDateTime = LocalDateTime.of(startDateTime.getYear(), startDateTime.getMonth(), startDateTime.getDayOfMonth(), j, k, 30).toInstant(ZoneOffset.UTC);
                    nodeManagementService.addNodeHistory(networkNodeData3, nodeStatus, localDateTime);
                }

            }
            log.info("fakeNode3 finished" + startDateTime);
            startDateTime = startDateTime.plusDays(1);
        }

        NetworkNodeData networkNodeData4 = new NetworkNodeData();
        networkNodeData4.setHash(fakeNode4);
        networkNodeData4.setNodeType(NodeType.FullNode);
        networkNodeData4.setAddress("test");
        networkNodeData4.setHttpPort("000");
        networkNodeData4.setPropagationPort("000");
        networkNodeData4.setReceivingPort("000");
        networkNodeData4.setNetworkType(NetworkType.TestNet);
        networkNodeData4.setTrustScore(77.0);
        networkNodeData4.setWebServerUrl("test");
        networkNodeData4.setFeeData(new FeeData(BigDecimal.valueOf(0.2), BigDecimal.valueOf(0.2), BigDecimal.valueOf(0.2)));
        networkNodeData4.setNodeSignature(new SignatureData("test", "test"));
        networkNodeData4.setNodeRegistrationData(new NodeRegistrationData());

        networkNodeData4.getNodeRegistrationData().setNodeHash(fakeNode4);
        networkNodeData4.getNodeRegistrationData().setNodeType(NodeType.FullNode.toString());
        networkNodeData4.getNodeRegistrationData().setNetworkType(NetworkType.TestNet.toString());
        networkNodeData4.getNodeRegistrationData().setCreationTime(Instant.now());
        networkNodeData4.getNodeRegistrationData().setRegistrarHash(new Hash("00"));
        networkNodeData4.getNodeRegistrationData().setRegistrarSignature(new SignatureData("test", "test"));

        eventDateTime = LocalDateTime.of(2019, 11, 15, 4, 41, 33).toInstant(ZoneOffset.UTC);
        nodeStatus = NetworkNodeStatus.ACTIVE;
        nodeManagementService.addNodeHistory(networkNodeData4, nodeStatus, eventDateTime);

        eventDateTime = LocalDateTime.of(2019, 12, 5, 18, 16, 3).toInstant(ZoneOffset.UTC);
        nodeStatus = NetworkNodeStatus.INACTIVE;
        nodeManagementService.addNodeHistory(networkNodeData4, nodeStatus, eventDateTime);

        eventDateTime = LocalDateTime.of(2019, 12, 10, 4, 41, 33).toInstant(ZoneOffset.UTC);
        nodeStatus = NetworkNodeStatus.ACTIVE;
        nodeManagementService.addNodeHistory(networkNodeData4, nodeStatus, eventDateTime);

        eventDateTime = LocalDateTime.of(2019, 12, 15, 18, 16, 3).toInstant(ZoneOffset.UTC);
        nodeStatus = NetworkNodeStatus.INACTIVE;
        nodeManagementService.addNodeHistory(networkNodeData4, nodeStatus, eventDateTime);

        eventDateTime = LocalDateTime.of(2019, 12, 20, 4, 41, 33).toInstant(ZoneOffset.UTC);
        nodeStatus = NetworkNodeStatus.ACTIVE;
        nodeManagementService.addNodeHistory(networkNodeData4, nodeStatus, eventDateTime);

        eventDateTime = LocalDateTime.of(2019, 12, 25, 18, 16, 3).toInstant(ZoneOffset.UTC);
        nodeStatus = NetworkNodeStatus.INACTIVE;
        nodeManagementService.addNodeHistory(networkNodeData4, nodeStatus, eventDateTime);

        log.info("fakeNode4 finished");

        NetworkNodeData networkNodeData5 = new NetworkNodeData();
        networkNodeData5.setHash(fakeNode5);
        networkNodeData5.setNodeType(NodeType.FullNode);
        networkNodeData5.setAddress("test");
        networkNodeData5.setHttpPort("000");
        networkNodeData5.setPropagationPort("000");
        networkNodeData5.setReceivingPort("000");
        networkNodeData5.setNetworkType(NetworkType.TestNet);
        networkNodeData5.setTrustScore(17.0);
        networkNodeData5.setWebServerUrl("test");
        networkNodeData5.setFeeData(new FeeData(BigDecimal.valueOf(0.1), BigDecimal.valueOf(0.1), BigDecimal.valueOf(0.1)));
        networkNodeData5.setNodeSignature(new SignatureData("test", "test"));
        networkNodeData5.setNodeRegistrationData(new NodeRegistrationData());

        networkNodeData5.getNodeRegistrationData().setNodeHash(fakeNode5);
        networkNodeData5.getNodeRegistrationData().setNodeType(NodeType.FullNode.toString());
        networkNodeData5.getNodeRegistrationData().setNetworkType(NetworkType.TestNet.toString());
        networkNodeData5.getNodeRegistrationData().setCreationTime(Instant.now());
        networkNodeData5.getNodeRegistrationData().setRegistrarHash(new Hash("00"));
        networkNodeData5.getNodeRegistrationData().setRegistrarSignature(new SignatureData("test", "test"));

        eventDateTime = LocalDateTime.of(2019, 11, 27, 10, 0, 0).toInstant(ZoneOffset.UTC);
        nodeStatus = NetworkNodeStatus.ACTIVE;
        nodeManagementService.addNodeHistory(networkNodeData5, nodeStatus, eventDateTime);

        log.info("fakeNode5 finished");

        NetworkNodeData networkNodeData6 = new NetworkNodeData();
        networkNodeData6.setHash(fakeNode6);
        networkNodeData6.setNodeType(NodeType.FullNode);
        networkNodeData6.setAddress("test");
        networkNodeData6.setHttpPort("000");
        networkNodeData6.setPropagationPort("000");
        networkNodeData6.setReceivingPort("000");
        networkNodeData6.setNetworkType(NetworkType.TestNet);
        networkNodeData6.setTrustScore(17.0);
        networkNodeData6.setWebServerUrl("test");
        networkNodeData6.setFeeData(new FeeData(BigDecimal.valueOf(0.1), BigDecimal.valueOf(0.1), BigDecimal.valueOf(0.1)));
        networkNodeData6.setNodeSignature(new SignatureData("test", "test"));
        networkNodeData6.setNodeRegistrationData(new NodeRegistrationData());

        networkNodeData6.getNodeRegistrationData().setNodeHash(fakeNode6);
        networkNodeData6.getNodeRegistrationData().setNodeType(NodeType.FullNode.toString());
        networkNodeData6.getNodeRegistrationData().setNetworkType(NetworkType.TestNet.toString());
        networkNodeData6.getNodeRegistrationData().setCreationTime(Instant.now());
        networkNodeData6.getNodeRegistrationData().setRegistrarHash(new Hash("00"));
        networkNodeData6.getNodeRegistrationData().setRegistrarSignature(new SignatureData("test", "test"));

        eventDateTime = LocalDateTime.of(2019, 11, 27, 10, 0, 0).toInstant(ZoneOffset.UTC);
        nodeStatus = NetworkNodeStatus.ACTIVE;
        nodeManagementService.addNodeHistory(networkNodeData6, nodeStatus, eventDateTime);

        eventDateTime = LocalDateTime.of(2020, 1, 1, 10, 0, 0).toInstant(ZoneOffset.UTC);
        nodeStatus = NetworkNodeStatus.INACTIVE;
        nodeManagementService.addNodeHistory(networkNodeData6, nodeStatus, eventDateTime);

        log.info("fakeNode6 finished");
    }
}