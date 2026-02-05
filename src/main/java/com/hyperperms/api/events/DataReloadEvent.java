package com.hyperperms.api.events;

import org.jetbrains.annotations.NotNull;

/**
 * Event fired when HyperPerms data is reloaded.
 * <p>
 * This event is fired when the plugin configuration and data is reloaded,
 * either via command or API call.
 */
public final class DataReloadEvent implements HyperPermsEvent {

    /**
     * The state of the reload event.
     */
    public enum State {
        /**
         * Before the reload starts.
         */
        PRE,

        /**
         * After the reload completes.
         */
        POST
    }

    private final State state;
    private final boolean configReloaded;
    private final boolean groupsReloaded;
    private final boolean tracksReloaded;
    private final boolean usersReloaded;

    /**
     * Creates a PRE reload event.
     */
    public DataReloadEvent() {
        this.state = State.PRE;
        this.configReloaded = false;
        this.groupsReloaded = false;
        this.tracksReloaded = false;
        this.usersReloaded = false;
    }

    /**
     * Creates a POST reload event with details about what was reloaded.
     *
     * @param configReloaded whether the config was reloaded
     * @param groupsReloaded whether groups were reloaded
     * @param tracksReloaded whether tracks were reloaded
     * @param usersReloaded  whether users were reloaded
     */
    public DataReloadEvent(boolean configReloaded, boolean groupsReloaded,
                           boolean tracksReloaded, boolean usersReloaded) {
        this.state = State.POST;
        this.configReloaded = configReloaded;
        this.groupsReloaded = groupsReloaded;
        this.tracksReloaded = tracksReloaded;
        this.usersReloaded = usersReloaded;
    }

    @Override
    public EventType getType() {
        return EventType.DATA_RELOAD;
    }

    /**
     * Gets the state of this event.
     *
     * @return the state
     */
    @NotNull
    public State getState() {
        return state;
    }

    /**
     * Checks if the configuration was reloaded.
     * <p>
     * Only meaningful for POST state.
     *
     * @return true if config was reloaded
     */
    public boolean isConfigReloaded() {
        return configReloaded;
    }

    /**
     * Checks if groups were reloaded.
     * <p>
     * Only meaningful for POST state.
     *
     * @return true if groups were reloaded
     */
    public boolean isGroupsReloaded() {
        return groupsReloaded;
    }

    /**
     * Checks if tracks were reloaded.
     * <p>
     * Only meaningful for POST state.
     *
     * @return true if tracks were reloaded
     */
    public boolean isTracksReloaded() {
        return tracksReloaded;
    }

    /**
     * Checks if users were reloaded.
     * <p>
     * Only meaningful for POST state.
     *
     * @return true if users were reloaded
     */
    public boolean isUsersReloaded() {
        return usersReloaded;
    }

    @Override
    public String toString() {
        if (state == State.PRE) {
            return "DataReloadEvent{state=PRE}";
        }
        return "DataReloadEvent{state=POST, configReloaded=" + configReloaded +
                ", groupsReloaded=" + groupsReloaded + ", tracksReloaded=" + tracksReloaded +
                ", usersReloaded=" + usersReloaded + "}";
    }
}
