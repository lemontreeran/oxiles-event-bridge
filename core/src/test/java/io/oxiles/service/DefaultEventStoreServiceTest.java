package io.oxiles.service;

import io.oxiles.dto.event.ContractEventDetails;
import io.oxiles.integration.eventstore.EventStore;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DefaultEventStoreServiceTest {

    private static final String EVENT_SIGNATURE = "signature";

    private static final String CONTRACT_ADDRESS = "0xd94a9d6733a64cecdcc8ca01da72554b4d883a47";

    private DefaultEventStoreService underTest;

    @Mock
    private EventStore mockEventStore;

    @Mock
    private ContractEventDetails mockEventDetails1;

    @Mock
    private ContractEventDetails mockEventDetails2;

    @Mock
    private Page<ContractEventDetails> mockPage;

    @Before
    public void init() {
        underTest = new DefaultEventStoreService(mockEventStore);
    }

    @Test
    public void testGetLatestContractEvent() {
        when(mockPage.getContent()).thenReturn(Arrays.asList(mockEventDetails1, mockEventDetails2));
        when(mockEventStore.getContractEventsForSignature(
                eq(EVENT_SIGNATURE), eq(CONTRACT_ADDRESS), any(PageRequest.class))).thenReturn(mockPage);
        assertEquals(mockEventDetails1, underTest.getLatestContractEvent(EVENT_SIGNATURE, CONTRACT_ADDRESS).get());
    }

    @Test
    public void testGetLatestContractEventNullEvents() {
        when(mockPage.getContent()).thenReturn(null);
        when(mockEventStore.getContractEventsForSignature(
                eq(EVENT_SIGNATURE), eq(CONTRACT_ADDRESS), any(PageRequest.class))).thenReturn(mockPage);
        assertEquals(false, underTest.getLatestContractEvent(EVENT_SIGNATURE, CONTRACT_ADDRESS).isPresent());
    }

    @Test
    public void testGetLatestContractEventEmptyEvents() {
        when(mockPage.getContent()).thenReturn(new ArrayList<>());
        when(mockEventStore.getContractEventsForSignature(
                eq(EVENT_SIGNATURE), eq(CONTRACT_ADDRESS), any(PageRequest.class))).thenReturn(mockPage);
        assertEquals(false, underTest.getLatestContractEvent(EVENT_SIGNATURE, CONTRACT_ADDRESS).isPresent());
    }
}
