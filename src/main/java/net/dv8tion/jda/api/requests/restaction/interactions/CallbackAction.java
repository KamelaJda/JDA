/*
 * Copyright 2015 Austin Keener, Michael Ritter, Florian Spieß, and the JDA contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dv8tion.jda.api.requests.restaction.interactions;

import net.dv8tion.jda.api.interactions.commands.InteractionHook;
import net.dv8tion.jda.api.requests.RestAction;

public interface CallbackAction extends RestAction<InteractionHook>
{
    enum ResponseType // TODO: Write better docs
    {
        /** Respond with a message, showing the user's input */
        CHANNEL_MESSAGE_WITH_SOURCE(4),
        /** ACK a command without sending a message, showing the user's input */
        DEFERRED_CHANNEL_MESSAGE_WITH_SOURCE(5),
        /** Defer the update of the message for a component interaction */
        DEFERRED_MESSAGE_UPDATE(6),
        /** Update the message for a component interaction */
        MESSAGE_UPDATE(7), // TODO: Support this
        ;
        private final int raw;

        ResponseType(int raw)
        {
            this.raw = raw;
        }

        public int getRaw()
        {
            return raw;
        }
    }
}