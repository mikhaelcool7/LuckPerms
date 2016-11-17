/*
 * Copyright (c) 2016 Lucko (Luck) <luck@lucko.me>
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.common.users;

import lombok.RequiredArgsConstructor;
import me.lucko.luckperms.api.Node;
import me.lucko.luckperms.common.LuckPermsPlugin;
import me.lucko.luckperms.common.utils.AbstractManager;
import me.lucko.luckperms.common.utils.Identifiable;
import me.lucko.luckperms.exceptions.ObjectAlreadyHasException;

import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
public class UserManager extends AbstractManager<UserIdentifier, User> {
    private final LuckPermsPlugin plugin;

    /**
     * Get a user object by name
     * @param name The name to search by
     * @return a {@link User} object if the user is loaded, returns null if the user is not loaded
     */
    public User get(String name) {
        for (User user : getAll().values()) {
            if (user.getName().equalsIgnoreCase(name)) {
                return user;
            }
        }
        return null;
    }

    public User get(UUID uuid) {
        return get(UserIdentifier.of(uuid, null));
    }

    /**
     * Set a user to the default group
     * @param user the user to give to
     */
    public boolean giveDefaultIfNeeded(User user, boolean save) {
        boolean hasGroup = false;

        if (user.getPrimaryGroup() != null && !user.getPrimaryGroup().isEmpty()) {
            for (Node node : user.getPermissions(false)) {
                if (node.isGroupNode()) {
                    hasGroup = true;
                    break;
                }
            }
        }

        if (hasGroup) {
            return false;
        }

        user.setPrimaryGroup("default");
        try {
            user.setPermission("group.default", true);
        } catch (ObjectAlreadyHasException ignored) {}

        if (save) {
            plugin.getStorage().saveUser(user);
        }

        return true;
    }

    public boolean shouldSave(User user) {
        if (user.getNodes().size() != 1) {
            return true;
        }

        for (Node node : user.getNodes()) {
            // There's only one.
            if (!node.isGroupNode()) {
                return true;
            }

            if (node.isTemporary() || node.isServerSpecific() || node.isWorldSpecific()) {
                return true;
            }

            if (!node.getGroupName().equalsIgnoreCase("default")) {
                // The user's only node is not the default group one.
                return true;
            }
        }

        if (!user.getPrimaryGroup().equalsIgnoreCase("default")) {
            return true; // Not in the default primary group
        }

        return false;
    }

    /**
     * Checks to see if the user is online, and if they are not, runs {@link #unload(Identifiable)}
     * @param user The user to be cleaned up
     */
    public void cleanup(User user) {
        if (!plugin.isOnline(plugin.getUuidCache().getExternalUUID(user.getUuid()))) {
            unload(user);
        }
    }

    /**
     * Reloads the data of all online users
     */
    public void updateAllUsers() {
        plugin.doSync(() -> {
            Set<UUID> players = plugin.getOnlinePlayers();
            plugin.doAsync(() -> {
                for (UUID uuid : players) {
                    UUID internal = plugin.getUuidCache().getUUID(uuid);
                    plugin.getStorage().loadUser(internal, "null").join();
                }
            });
        });
    }

    @Override
    public User apply(UserIdentifier id) {
        return id.getUsername() == null ?
                new User(id.getUuid(), plugin) :
                new User(id.getUuid(), id.getUsername(), plugin);
    }
}
