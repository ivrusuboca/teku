/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.beaconrestapi.handlers.beacon;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_GONE;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.EPOCH;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.ROOT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SLOT;

import com.google.common.primitives.UnsignedLong;
import io.javalin.http.Context;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystem;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.util.config.StateStorageMode;

public class GetStateTest {
  private final StorageSystem storageSystem =
      InMemoryStorageSystem.createEmptyLatestStorageSystem(StateStorageMode.ARCHIVE);
  private tech.pegasys.teku.datastructures.state.BeaconState beaconStateInternal;
  private BeaconState beaconState;
  private Bytes32 blockRoot;
  private UnsignedLong slot;

  private final JsonProvider jsonProvider = new JsonProvider();
  private final Context context = mock(Context.class);
  private final String missingRoot = Bytes32.leftPad(Bytes.fromHexString("0xff")).toHexString();
  private final ChainDataProvider dataProvider = mock(ChainDataProvider.class);

  @BeforeEach
  public void setup() {
    slot = UnsignedLong.valueOf(10);
    storageSystem.chainUpdater().initializeGenesis();
    SignedBlockAndState bestBlock = storageSystem.chainUpdater().advanceChain(slot);
    storageSystem.chainUpdater().updateBestBlock(bestBlock);

    beaconStateInternal = bestBlock.getState();
    blockRoot = bestBlock.getRoot();
    beaconState = new BeaconState(beaconStateInternal);
  }

  @Test
  public void shouldReturnNotFoundWhenQueryAgainstMissingRootObject() throws Exception {
    final GetState handler = new GetState(dataProvider, jsonProvider);

    when(dataProvider.isStoreAvailable()).thenReturn(true);
    when(context.queryParamMap()).thenReturn(Map.of(ROOT, List.of(missingRoot)));
    when(dataProvider.getStateByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    handler.handle(context);

    verify(context).status(SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnBadRequestWhenNoParameterSpecified() throws Exception {
    final GetState handler = new GetState(dataProvider, jsonProvider);

    when(context.queryParamMap()).thenReturn(Collections.emptyMap());

    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestWhenBadSlotSpecified() throws Exception {
    final GetState handler = new GetState(dataProvider, jsonProvider);

    when(dataProvider.isStoreAvailable()).thenReturn(true);
    when(context.queryParamMap()).thenReturn(Map.of(SLOT, List.of("not-an-int")));

    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestWhenBadParamSpecified() throws Exception {
    final GetState handler = new GetState(dataProvider, jsonProvider);

    when(dataProvider.isStoreAvailable()).thenReturn(true);
    when(context.queryParamMap()).thenReturn(Map.of(EPOCH, List.of("not-an-int")));

    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBeaconStateObjectWhenQueryByRoot() throws Exception {
    final GetState handler = new GetState(dataProvider, jsonProvider);

    when(dataProvider.isStoreAvailable()).thenReturn(true);
    when(context.queryParamMap()).thenReturn(Map.of(ROOT, List.of(blockRoot.toHexString())));
    when(dataProvider.getStateByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconState)));

    handler.handle(context);

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<SafeFuture<String>> args = ArgumentCaptor.forClass(SafeFuture.class);
    verify(context).result(args.capture());
    SafeFuture<String> data = args.getValue();
    assertEquals(data.get(), jsonProvider.objectToJSON(beaconState));
  }

  @Test
  public void shouldReturnBadRequestWhenEmptyRootIsSpecified() throws Exception {
    final GetState handler = new GetState(dataProvider, jsonProvider);

    when(dataProvider.isStoreAvailable()).thenReturn(true);
    when(context.queryParamMap()).thenReturn(Map.of(ROOT, List.of()));

    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestWhenEmptySlotIsSpecified() throws Exception {
    final GetState handler = new GetState(dataProvider, jsonProvider);

    when(dataProvider.isStoreAvailable()).thenReturn(true);
    when(context.queryParamMap()).thenReturn(Map.of(SLOT, List.of()));

    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestWhenMultipleParametersSpecified() throws Exception {
    final GetState handler = new GetState(dataProvider, jsonProvider);

    when(dataProvider.isStoreAvailable()).thenReturn(true);
    when(context.queryParamMap()).thenReturn(Map.of(SLOT, List.of(), ROOT, List.of()));

    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBeaconStateObjectWhenQueryBySlot() throws Exception {
    final GetState handler = new GetState(dataProvider, jsonProvider);

    when(dataProvider.isStoreAvailable()).thenReturn(true);
    when(context.queryParamMap()).thenReturn(Map.of(SLOT, List.of(slot.toString())));
    when(dataProvider.getStateAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconState)));

    handler.handle(context);

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<SafeFuture<String>> args = ArgumentCaptor.forClass(SafeFuture.class);
    verify(context).result(args.capture());
    SafeFuture<String> data = args.getValue();
    assertEquals(data.get(), jsonProvider.objectToJSON(beaconState));
  }

  @Test
  public void shouldHandleMissingStateAtFinalizedSlot() throws Exception {
    final GetState handler = new GetState(dataProvider, jsonProvider);
    final UnsignedLong slot = UnsignedLong.valueOf(11223344L);

    when(dataProvider.isStoreAvailable()).thenReturn(true);
    when(context.queryParamMap()).thenReturn(Map.of(SLOT, List.of(slot.toString())));
    when(dataProvider.isFinalized(slot)).thenReturn(true);
    when(dataProvider.getStateAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    handler.handle(context);

    verify(context).status(SC_GONE);
  }

  @Test
  public void shouldHandleMissingStateAtNonFinalSlot() throws Exception {
    final GetState handler = new GetState(dataProvider, jsonProvider);
    final UnsignedLong slot = UnsignedLong.valueOf(11223344L);

    when(dataProvider.isStoreAvailable()).thenReturn(true);
    when(context.queryParamMap()).thenReturn(Map.of(SLOT, List.of(slot.toString())));
    when(dataProvider.isFinalized(slot)).thenReturn(false);
    when(dataProvider.getStateAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    handler.handle(context);

    verify(context).status(SC_NOT_FOUND);
  }
}
