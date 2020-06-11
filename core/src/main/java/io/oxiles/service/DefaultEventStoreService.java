package io.oxiles.service;

import io.oxiles.dto.event.ContractEventDetails;
import io.oxiles.integration.eventstore.EventStore;
import io.oxiles.model.LatestBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * @{inheritDoc}
 *
 * @author Craig Williams <craig.williams@consensys.net>
 */
@Component
public class DefaultEventStoreService implements EventStoreService {

    private EventStore eventStore;

    public DefaultEventStoreService(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    /**
     * @{inheritDoc}
     */
    @Override
    public Optional<ContractEventDetails> getLatestContractEvent(
            String eventSignature, String contractAddress) {
        int page = eventStore.isPagingZeroIndexed() ? 0 : 1;

        final PageRequest pagination = new PageRequest(page,
                1, new Sort(Sort.Direction.DESC, "blockNumber"));

        final Page<ContractEventDetails> eventsPage =
                eventStore.getContractEventsForSignature(eventSignature, contractAddress, pagination);

        if (eventsPage == null) {
            return Optional.empty();
        }

        final List<ContractEventDetails> events = eventsPage.getContent();

        if (events == null || events.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(events.get(0));
    }

    @Override
    public Optional<LatestBlock> getLatestBlock(String nodeName) {

        return eventStore.getLatestBlockForNode(nodeName);
    }
}
