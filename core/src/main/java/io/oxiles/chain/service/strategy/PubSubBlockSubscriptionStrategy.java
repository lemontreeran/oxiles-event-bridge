package io.oxiles.chain.service.strategy;

import io.oxiles.model.LatestBlock;
import io.oxiles.service.AsyncTaskService;
import io.oxiles.service.EventStoreService;
import io.oxiles.utils.ExecutorNameFactory;
import io.reactivex.disposables.Disposable;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import io.oxiles.chain.service.BlockchainException;
import io.oxiles.chain.service.domain.Block;
import io.oxiles.chain.service.domain.wrapper.Web3jBlock;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.websocket.events.NewHead;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;

@Slf4j
public class PubSubBlockSubscriptionStrategy extends AbstractBlockSubscriptionStrategy<NewHead> {

    private static final String PUB_SUB_EXECUTOR_NAME = "PUBSUB";

    private RetryTemplate retryTemplate;

    private AsyncTaskService asyncService;

    public PubSubBlockSubscriptionStrategy(Web3j web3j,
                                           String nodeName,
                                           EventStoreService eventStoreService,
                                           BigInteger maxUnsyncedBlocksForFilter,
                                           AsyncTaskService asyncService) {
        super(web3j, nodeName, eventStoreService, maxUnsyncedBlocksForFilter, asyncService);

        this.asyncService = asyncService;
    }

    @Override
    public Disposable subscribe() {
        final Optional<LatestBlock> latestBlock = getLatestBlock();

        if (latestBlock.isPresent()) {

            BigInteger latestBlockNumber = latestBlock.get().getNumber();

            try {

                BigInteger currentBlockNumber = web3j.ethBlockNumber().send().getBlockNumber();

                BigInteger cappedBlockNumber = BigInteger.valueOf(0);

                if (currentBlockNumber.subtract(latestBlockNumber).compareTo(maxUnsyncedBlocksForFilter) == 1) {

                    cappedBlockNumber = currentBlockNumber.subtract(maxUnsyncedBlocksForFilter);
                    log.info("BLOCK: Max Unsynced Blocks gap reached ´{} to {} . Applied {}. Max {}", latestBlockNumber, currentBlockNumber, cappedBlockNumber, maxUnsyncedBlocksForFilter);
                    latestBlockNumber = cappedBlockNumber;
                }
            }
            catch (Exception e){
                log.error("Could not get current block to possibly cap range",e);
            }

            final DefaultBlockParameter blockParam = DefaultBlockParameter.valueOf(latestBlockNumber);

            //New heads can only start from latest block so we need to obtain missing blocks first
            blockSubscription = web3j.replayPastBlocksFlowable(blockParam, true)
                    .doOnComplete(() -> blockSubscription = subscribeToNewHeads())
                    .subscribe(ethBlock -> triggerListeners(convertToEventeumBlock(ethBlock)));
        } else {
            blockSubscription = subscribeToNewHeads();
        }

        return blockSubscription;
    }

    private Disposable subscribeToNewHeads() {
        final Disposable disposable = web3j.newHeadsNotifications().subscribe(newHead -> {
            //Need to execute this is a seperate thread to workaround websocket thread deadlock
            asyncService.execute(ExecutorNameFactory.build(PUB_SUB_EXECUTOR_NAME, nodeName),
                    () -> triggerListeners(newHead.getParams().getResult()));
        });

        if (disposable.isDisposed()) {
            throw new BlockchainException("Error when subscribing to newheads.  Disposable already disposed.");
        }

        return disposable;
    }

    NewHead convertToNewHead(EthBlock ethBlock) {
        final BasicNewHead newHead = new BasicNewHead();
        newHead.setHash(ethBlock.getBlock().getHash());
        newHead.setNumber(ethBlock.getBlock().getNumberRaw());
        newHead.setTimestamp(ethBlock.getBlock().getTimestampRaw());

        return newHead;
    }

    @Override
    Block convertToEventeumBlock(NewHead blockObject) {
        return new Web3jBlock(getEthBlock(blockObject.getHash()).getBlock(), nodeName);
    }

    Block convertToEventeumBlock(EthBlock blockObject) {
        return new Web3jBlock(blockObject.getBlock(), nodeName);
    }

    protected RetryTemplate getRetryTemplate() {
        if (retryTemplate == null) {
            retryTemplate = new RetryTemplate();

            final FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
            fixedBackOffPolicy.setBackOffPeriod(500);
            retryTemplate.setBackOffPolicy(fixedBackOffPolicy);

            final SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
            retryPolicy.setMaxAttempts(10);
            retryTemplate.setRetryPolicy(retryPolicy);
        }

        return retryTemplate;
    }

    private EthBlock getEthBlock(String blockHash) {
        return getRetryTemplate().execute((context) -> {
            try {
                final EthBlock block = web3j.ethGetBlockByHash(blockHash, true).send();

                if (block == null || block.getBlock() == null) {
                    throw new BlockchainException(String.format("Block not found. Hash: %s", blockHash));
                }

                return block;
            } catch (IOException e) {
                throw new BlockchainException("Unable to retrieve block details", e);
            }
        });
    }

    @Setter
    private class BasicNewHead extends NewHead {
        private String hash;

        private String number;

        private String timestamp;

        @Override
        public String getHash() {
            return hash;
        }

        @Override
        public String getNumber() {
            return number;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }
    }
}
