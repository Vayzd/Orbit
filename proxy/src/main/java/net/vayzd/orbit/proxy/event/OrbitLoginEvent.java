/*
 * This file is part of Orbit, licenced under the MIT Licence (MIT)
 *
 * Copyright (c) Vayzd Network <https://www.vayzd.net/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.vayzd.orbit.proxy.event;

import lombok.*;
import net.md_5.bungee.api.*;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.connection.*;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.*;
import net.vayzd.orbit.datastore.group.*;

/**
 * Event called when a player is already logging in.
 * <p>
 * Whilst {@link LoginEvent} is called, this event can be
 * used to check check whether a player has certain permission
 * attachments. In the normal implementation of the
 * {@link LoginEvent} this wouldn't be possible, since there is
 * no {@link ProxiedPlayer} reference yet to store this data in.
 */
@EqualsAndHashCode(callSuper = false)
@ToString
@Data
public class OrbitLoginEvent extends AsyncEvent<OrbitLoginEvent> implements Cancellable {

    private final PendingConnection connection;
    private final DatastoreSubject subject;
    private boolean cancelled;
    private BaseComponent[] cancelReason;

    public OrbitLoginEvent(LoginEvent event, DatastoreSubject subject, Callback<OrbitLoginEvent> done) {
        super(done);
        this.subject = subject;
        this.connection = event.getConnection();
    }
}
