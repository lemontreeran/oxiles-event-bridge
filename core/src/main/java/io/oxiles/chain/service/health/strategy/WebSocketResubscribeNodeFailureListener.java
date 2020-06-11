package io.oxiles.chain.service.health.strategy;

import io.oxiles.chain.service.BlockchainService;
import io.oxiles.chain.websocket.WebSocketReconnectionManager;
import io.oxiles.service.SubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.web3j.protocol.websocket.WebSocketClient;

/**
 * An NodeFailureListener that reconnects to the websocket server on failure, and
 * reconnects the blockchain service and resubscribes to all
 * active event subscriptions on recovery.
 *
 * Note:  All subscriptions are unregistered before being reregistered.
 *
 * @author Craig Williams <craig.williams@consensys.net>
 */
@Slf4j
public class WebSocketResubscribeNodeFailureListener extends ResubscribingReconnectionStrategy {

    private WebSocketReconnectionManager reconnectionManager;
    private WebSocketClient client;
    private BlockchainService blockchainService;

    public WebSocketResubscribeNodeFailureListener(SubscriptionService subscriptionService,
                                                   BlockchainService blockchainService,
                                                   WebSocketReconnectionManager reconnectionManager,
                                                   WebSocketClient client) {
        super(subscriptionService, blockchainService);

        this.reconnectionManager = reconnectionManager;
        this.client = client;
        this.blockchainService = blockchainService;
    }

    @Override
    public void reconnect() {
        log.info("Reconnecting web socket because of {} node failure", blockchainService.getNodeName());
        reconnectionManager.reconnect(client);
    }
}
