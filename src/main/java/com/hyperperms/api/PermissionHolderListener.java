package com.hyperperms.api;

import com.hyperperms.api.context.ContextSet;
import com.hyperperms.model.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Listener interface for permission holder modifications.
 * <p>
 * This interface allows managers to be notified when User or Group objects
 * are modified, enabling them to fire appropriate events.
 * <p>
 * Implementations should be lightweight as callbacks are made synchronously
 * during the modification operation.
 */
public interface PermissionHolderListener {

    /**
     * Called when a node is added to a permission holder.
     *
     * @param holder the holder that was modified
     * @param node   the node that was added
     * @param result the result of the add operation
     */
    void onNodeAdded(@NotNull PermissionHolder holder, @NotNull Node node,
                     @NotNull PermissionHolder.DataMutateResult result);

    /**
     * Called when a node is removed from a permission holder.
     *
     * @param holder the holder that was modified
     * @param node   the node that was removed
     * @param result the result of the remove operation
     */
    void onNodeRemoved(@NotNull PermissionHolder holder, @NotNull Node node,
                       @NotNull PermissionHolder.DataMutateResult result);

    /**
     * Called when a node is set (replaced) on a permission holder.
     *
     * @param holder the holder that was modified
     * @param node   the node that was set
     * @param result the result of the set operation
     */
    void onNodeSet(@NotNull PermissionHolder holder, @NotNull Node node,
                   @NotNull PermissionHolder.DataMutateResult result);

    /**
     * Called when a group is added to a permission holder.
     *
     * @param holder    the holder that was modified
     * @param groupName the name of the group that was added
     * @param result    the result of the add operation
     */
    void onGroupAdded(@NotNull PermissionHolder holder, @NotNull String groupName,
                      @NotNull PermissionHolder.DataMutateResult result);

    /**
     * Called when a group is removed from a permission holder.
     *
     * @param holder    the holder that was modified
     * @param groupName the name of the group that was removed
     * @param result    the result of the remove operation
     */
    void onGroupRemoved(@NotNull PermissionHolder holder, @NotNull String groupName,
                        @NotNull PermissionHolder.DataMutateResult result);

    /**
     * Called when all nodes are cleared from a permission holder.
     *
     * @param holder   the holder that was modified
     * @param contexts the contexts that were cleared, or null if all nodes were cleared
     */
    void onNodesCleared(@NotNull PermissionHolder holder, @Nullable ContextSet contexts);

    /**
     * A no-op listener implementation that does nothing.
     */
    PermissionHolderListener EMPTY = new PermissionHolderListener() {
        @Override
        public void onNodeAdded(@NotNull PermissionHolder holder, @NotNull Node node,
                                @NotNull PermissionHolder.DataMutateResult result) {}

        @Override
        public void onNodeRemoved(@NotNull PermissionHolder holder, @NotNull Node node,
                                  @NotNull PermissionHolder.DataMutateResult result) {}

        @Override
        public void onNodeSet(@NotNull PermissionHolder holder, @NotNull Node node,
                              @NotNull PermissionHolder.DataMutateResult result) {}

        @Override
        public void onGroupAdded(@NotNull PermissionHolder holder, @NotNull String groupName,
                                 @NotNull PermissionHolder.DataMutateResult result) {}

        @Override
        public void onGroupRemoved(@NotNull PermissionHolder holder, @NotNull String groupName,
                                   @NotNull PermissionHolder.DataMutateResult result) {}

        @Override
        public void onNodesCleared(@NotNull PermissionHolder holder, @Nullable ContextSet contexts) {}
    };
}
