package io.oxiles.dto.transaction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@ToString
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDetails {

    private String hash;
    private String nonce;
    private String blockHash;
    private String blockNumber;
    private String blockTimestamp;
    private String transactionIndex;
    private String from;
    private String to;
    private String value;
    private String nodeName;
    private String contractAddress;
    private String input;
    private String revertReason;

    private TransactionStatus status;
}
