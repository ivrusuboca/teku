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

package tech.pegasys.teku.storage.client;

import static com.google.common.base.Preconditions.checkState;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.StateTransitionException;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.util.config.Constants;

public class ChainUpdater {

  public final RecentChainData recentChainData;
  public final ChainBuilder chainBuilder;

  public ChainUpdater(final RecentChainData recentChainData, final ChainBuilder chainBuilder) {
    this.recentChainData = recentChainData;
    this.chainBuilder = chainBuilder;
  }

  public void setCurrentSlot(final UnsignedLong currentSlot) {
    checkState(!recentChainData.isPreGenesis(), "Cannot set current slot before genesis");
    setTime(getSlotTime(currentSlot));
  }

  public void setTime(final UnsignedLong time) {
    checkState(!recentChainData.isPreGenesis(), "Cannot set time before genesis");
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setTime(time);
    tx.commit().join();
  }

  public SignedBlockAndState addNewBestBlock() {
    try {
      final SignedBlockAndState nextBlock;
      nextBlock = chainBuilder.generateNextBlock();
      updateBestBlock(nextBlock);
      return nextBlock;
    } catch (StateTransitionException e) {
      throw new IllegalStateException(e);
    }
  }

  public SignedBlockAndState initializeGenesis() {
    return initializeGenesis(true);
  }

  public SignedBlockAndState initializeGenesis(final boolean signDeposits) {
    final SignedBlockAndState genesis = chainBuilder.generateGenesis(signDeposits);
    recentChainData.initializeFromGenesis(genesis.getState());
    return genesis;
  }

  public SignedBlockAndState finalizeEpoch(final long epoch) {
    return finalizeEpoch(UnsignedLong.valueOf(epoch));
  }

  public SignedBlockAndState finalizeEpoch(final UnsignedLong epoch) {

    final SignedBlockAndState blockAndState =
        chainBuilder.getLatestBlockAndStateAtEpochBoundary(epoch);
    final Checkpoint checkpoint = new Checkpoint(epoch, blockAndState.getRoot());

    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(checkpoint);
    tx.commit().reportExceptions();

    return blockAndState;
  }

  public void updateBestBlock(final SignedBlockAndState bestBlock) {
    saveBlock(bestBlock);

    recentChainData.updateBestBlock(bestBlock.getRoot(), bestBlock.getSlot());
  }

  public SignedBlockAndState advanceChain() {
    try {
      final SignedBlockAndState block = chainBuilder.generateNextBlock();
      saveBlock(block);
      return block;
    } catch (StateTransitionException e) {
      throw new IllegalStateException(e);
    }
  }

  public SignedBlockAndState advanceChain(final long slot) {
    return advanceChain(UnsignedLong.valueOf(slot));
  }

  public SignedBlockAndState advanceChain(final UnsignedLong slot) {
    try {
      final SignedBlockAndState block = chainBuilder.generateBlockAtSlot(slot);
      saveBlock(block);
      return block;
    } catch (StateTransitionException e) {
      throw new IllegalStateException(e);
    }
  }

  public void saveBlock(final SignedBlockAndState block) {
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.putBlockAndState(block.getBlock(), block.getState());
    assertThat(tx.commit()).isCompleted();
    recentChainData
        .getForkChoiceStrategy()
        .orElseThrow()
        .onBlock(block.getBlock().getMessage(), block.getState());

    // Make sure time is consistent with block
    final UnsignedLong blockTime = getSlotTime(block.getSlot());
    if (blockTime.compareTo(recentChainData.getStore().getTime()) > 0) {
      setTime(blockTime);
    }
  }

  protected UnsignedLong getSlotTime(final UnsignedLong slot) {
    final UnsignedLong secPerSlot = UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT);
    return recentChainData.getGenesisTime().plus(slot.times(secPerSlot));
  }
}
