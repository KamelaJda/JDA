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

package net.dv8tion.jda.internal.requests.restaction;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.ActionRow;
import net.dv8tion.jda.api.requests.Request;
import net.dv8tion.jda.api.requests.Response;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageAction;
import net.dv8tion.jda.api.utils.AttachmentOption;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.requests.Requester;
import net.dv8tion.jda.internal.requests.Route;
import net.dv8tion.jda.internal.utils.AllowedMentionsUtil;
import net.dv8tion.jda.internal.utils.Checks;
import net.dv8tion.jda.internal.utils.IOUtil;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.*;

public class WebhookMessageActionImpl
    extends TriggerRestAction<Message>
    implements WebhookMessageAction
{
    private final StringBuilder content = new StringBuilder();
    private final List<MessageEmbed> embeds = new ArrayList<>();
    private final Map<String, InputStream> files = new HashMap<>();
    private final AllowedMentionsUtil allowedMentions = new AllowedMentionsUtil();
    private final List<ActionRow> components = new ArrayList<>();
    private final MessageChannel channel;

    private boolean ephemeral, tts;
    private String username, avatarUrl;

    public WebhookMessageActionImpl(JDA api, MessageChannel channel, Route.CompiledRoute route)
    {
        super(api, route);
        this.channel = channel;
    }

    @Nonnull
    public WebhookMessageActionImpl applyMessage(@Nonnull Message message)
    {
        Checks.notNull(message, "Message");
        this.tts = message.isTTS();
        this.embeds.addAll(message.getEmbeds());
        this.allowedMentions.applyMessage(message);
        this.components.addAll(message.getActionRows());
        return setContent(message.getContentRaw());
    }

    @Nonnull
    @Override
    public WebhookMessageActionImpl setEphemeral(boolean ephemeral)
    {
        this.ephemeral = ephemeral;
        return this;
    }

    @Nonnull
    @Override
    public WebhookMessageActionImpl setContent(@Nullable String content)
    {
        this.content.setLength(0);
        if (content != null)
            this.content.append(content);
        return this;
    }

    @Nonnull
    @Override
    public WebhookMessageActionImpl setTTS(boolean tts)
    {
        this.tts = tts;
        return this;
    }

    @Nonnull
    @Override
    public WebhookMessageActionImpl setUsername(@Nullable String name)
    {
        if (name != null)
        {
            Checks.notEmpty(name, "Name");
            Checks.notLonger(name, 128, "Name");
        }
        this.username = name;
        return this;
    }

    @Nonnull
    @Override
    public WebhookMessageActionImpl setAvatarUrl(@Nullable String iconUrl)
    {
        if (iconUrl != null && iconUrl.isEmpty())
            iconUrl = null;
        this.avatarUrl = iconUrl;
        return this;
    }

    @Nonnull
    @Override
    public WebhookMessageActionImpl addEmbeds(@Nonnull Collection<? extends MessageEmbed> embeds)
    {
        Checks.noneNull(embeds, "Message Embeds");
        Checks.check(this.embeds.size() + embeds.size() <= 10, "Cannot have more than 10 embeds in a message!");
        this.embeds.addAll(embeds);
        return this;
    }

    @Nonnull
    @Override
    public WebhookMessageActionImpl addFile(@Nonnull String name, @Nonnull InputStream data, @Nonnull AttachmentOption... options)
    {
        Checks.notNull(name, "Name");
        Checks.notNull(data, "Data");
        Checks.notNull(options, "AttachmentOption");
        // Yes < 10 not <= 10 since we add one after this
        Checks.check(files.size() < 10, "Cannot have more than 10 files in a message!");
        if (options.length > 0 && options[0] == AttachmentOption.SPOILER)
            name = "SPOILER_" + name;
        files.put(name, data);
        return this;
    }

    @Nonnull
    @Override
    public WebhookMessageActionImpl addActionRows(@Nonnull ActionRow... rows)
    {
        Checks.noneNull(rows, "ActionRows");
        Checks.check(rows.length + components.size() <= 5, "Can only have 5 action rows per message!");
        Collections.addAll(components, rows);
        return this;
    }

    private DataObject getJSON()
    {
        DataObject json = DataObject.empty();
        json.put("content", content.toString());
        json.put("tts", tts);

        if (username != null)
            json.put("username", username); // TODO: this doesn't work for interactions (?)
        if (avatarUrl != null)
            json.put("avatar_url", avatarUrl);
        if (ephemeral)
            json.put("flags", 64);
        if (!embeds.isEmpty())
            json.put("embeds", DataArray.fromCollection(embeds));
        if (!components.isEmpty())
            json.put("components", DataArray.fromCollection(components));
        json.put("allowed_mentions", allowedMentions);
        return json;
    }

    @Override
    protected RequestBody finalizeData()
    {
        DataObject json = getJSON();
        if (files.isEmpty())
            return getRequestBody(json);

        MultipartBody.Builder body = new MultipartBody.Builder().setType(MultipartBody.FORM);
        int i = 0;
        for (Map.Entry<String, InputStream> file : files.entrySet())
        {
            RequestBody stream = IOUtil.createRequestBody(Requester.MEDIA_TYPE_OCTET, file.getValue());
            body.addFormDataPart("file" + i++, file.getKey(), stream);
        }

        body.addFormDataPart("payload_json", json.toString());
        files.clear();
        return body.build();
    }

    @Override
    protected void handleSuccess(Response response, Request<Message> request)
    {
        // TODO: Create new message object that uses the webhook endpoints to edit/delete
        // TODO: This could be a simple decorator or proxy implementation
        Message message = request.getJDA().getEntityBuilder().createMessage(response.getObject(), channel, false);
        request.onSuccess(message);
    }

    @Nonnull
    @Override
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public WebhookMessageActionImpl mentionRepliedUser(boolean mention)
    {
        allowedMentions.mentionRepliedUser(mention);
        return this;
    }

    @Nonnull
    @Override
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public WebhookMessageActionImpl allowedMentions(@Nullable Collection<Message.MentionType> allowedMentions)
    {
        this.allowedMentions.allowedMentions(allowedMentions);
        return this;
    }

    @Nonnull
    @Override
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public WebhookMessageActionImpl mention(@Nonnull IMentionable... mentions)
    {
        allowedMentions.mention(mentions);
        return this;
    }

    @Nonnull
    @Override
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public WebhookMessageActionImpl mentionUsers(@Nonnull String... userIds)
    {
        allowedMentions.mentionUsers(userIds);
        return this;
    }

    @Nonnull
    @Override
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public WebhookMessageActionImpl mentionRoles(@Nonnull String... roleIds)
    {
        allowedMentions.mentionRoles(roleIds);
        return this;
    }
}
