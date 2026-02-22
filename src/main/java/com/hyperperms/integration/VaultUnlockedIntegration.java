package com.hyperperms.integration;

import com.hyperperms.HyperPerms;
import com.hyperperms.util.Logger;
import com.hyperperms.util.ReflectionUtil;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Integration with VaultUnlocked to register HyperPerms as a permission provider.
 * Uses pure reflection to avoid compile-time dependency on VaultUnlocked.
 */
public final class VaultUnlockedIntegration {

    private static volatile boolean initialized = false;
    private static volatile Object provider = null;
    private static volatile Object servicesManager = null;

    private VaultUnlockedIntegration() {}

    /**
     * Initializes the VaultUnlocked integration.
     * Registers HyperPerms as a permission provider if VaultUnlocked is available.
     *
     * @param hyperPerms the HyperPerms instance
     */
    public static void init(HyperPerms hyperPerms) {
        if (initialized) {
            return;
        }

        Logger.debugIntegration("Checking for VaultUnlocked availability...");
        if (!isVaultUnlockedAvailable()) {
            Logger.info("VaultUnlocked not found - integration disabled");
            return;
        }
        Logger.debugIntegration("VaultUnlocked detected, registering permission provider");

        try {
            // Create the provider
            provider = new HyperPermsVaultProvider(hyperPerms);

            // Get VaultUnlockedServicesManager singleton
            Class<?> servicesManagerClass = Class.forName("net.cfh.vault.VaultUnlockedServicesManager");
            Method getMethod = servicesManagerClass.getMethod("get");
            servicesManager = getMethod.invoke(null);

            if (servicesManager == null) {
                Logger.warn("VaultUnlocked services manager is null - integration disabled");
                return;
            }

            // Register our permission provider
            Class<?> permissionUnlockedClass = Class.forName("net.milkbowl.vault2.permission.PermissionUnlocked");
            Method permissionMethod = servicesManagerClass.getMethod("permission", permissionUnlockedClass);
            permissionMethod.invoke(servicesManager, provider);

            initialized = true;
            Logger.info("VaultUnlocked integration enabled - HyperPerms registered as permission provider");

        } catch (Exception e) {
            Logger.warn("Failed to initialize VaultUnlocked integration: %s", e.getMessage());
            provider = null;
            servicesManager = null;
        }
    }

    /**
     * Shuts down the VaultUnlocked integration.
     * Unregisters HyperPerms as a permission provider.
     */
    public static void shutdown() {
        if (!initialized || provider == null || servicesManager == null) {
            return;
        }

        try {
            // Unregister our permission provider
            Class<?> servicesManagerClass = Class.forName("net.cfh.vault.VaultUnlockedServicesManager");
            Class<?> permissionUnlockedClass = Class.forName("net.milkbowl.vault2.permission.PermissionUnlocked");
            Method unregisterMethod = servicesManagerClass.getMethod("unregister", permissionUnlockedClass);
            unregisterMethod.invoke(servicesManager, provider);

            Logger.info("VaultUnlocked integration disabled - HyperPerms unregistered");

        } catch (Exception e) {
            Logger.warn("Failed to unregister VaultUnlocked provider: %s", e.getMessage());
        } finally {
            initialized = false;
            provider = null;
            servicesManager = null;
        }
    }

    /**
     * Checks if VaultUnlocked is available on the server.
     *
     * @return true if VaultUnlocked classes are available
     */
    public static boolean isVaultUnlockedAvailable() {
        return ReflectionUtil.isClassAvailable("net.cfh.vault.VaultUnlockedServicesManager");
    }

    /**
     * Checks if the integration is currently active.
     *
     * @return true if HyperPerms is registered as a VaultUnlocked provider
     */
    public static boolean isActive() {
        return initialized;
    }

    /**
     * Gets the provider instance if available.
     *
     * @return the provider instance, or null if not initialized
     */
    @Nullable
    public static Object getProvider() {
        return provider;
    }
}
