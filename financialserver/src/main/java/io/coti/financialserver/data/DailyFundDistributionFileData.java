package io.coti.financialserver.data;

import io.coti.basenode.crypto.CryptoHelper;
import io.coti.basenode.data.Hash;
import io.coti.basenode.data.interfaces.IEntity;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Data
public class DailyFundDistributionFileData implements IEntity {

    private Hash hash;
    @NotEmpty
    private Instant date;
    @NotEmpty
    private String fileName;

    public DailyFundDistributionFileData(@NotEmpty Instant date, @NotEmpty String fileName) {
        this.date = date;
        this.fileName = fileName;
        initHashByDate();
    }

    public void initHashByDate() {
        LocalDateTime ldt = LocalDateTime.ofInstant(date, ZoneOffset.UTC);
        this.hash = CryptoHelper.cryptoHash( (ldt.getYear() + ldt.getMonth().toString() +
                ldt.getDayOfMonth()).getBytes() );
    }
}
