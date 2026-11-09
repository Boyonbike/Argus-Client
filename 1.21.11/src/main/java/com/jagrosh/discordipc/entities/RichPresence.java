package com.jagrosh.discordipc.entities;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Objects;

public class RichPresence {
   private static final int MIN_ALLOWED_BUTTONS = 1;
   private static final int MAX_ALLOWED_BUTTONS = 2;
   private final ActivityType activityType;
   private final StatusDisplayType statusDisplayType;
   private final String state;
   private final String stateUrl;
   private final String details;
   private final String detailsUrl;
   private final String name;
   private final long startTimestamp;
   private final long endTimestamp;
   private final String largeImageKey;
   private final String largeImageText;
   private final String largeImageUrl;
   private final String smallImageKey;
   private final String smallImageText;
   private final String smallImageUrl;
   private final String partyId;
   private final int partySize;
   private final int partyMax;
   private final PartyPrivacy partyPrivacy;
   private final String matchSecret;
   private final String joinSecret;
   private final String spectateSecret;
   private final JsonArray buttons;
   private final boolean instance;

   public RichPresence(
      ActivityType activityType,
      StatusDisplayType statusDisplayType,
      String state,
      String stateUrl,
      String details,
      String detailsUrl,
      String name,
      long startTimestamp,
      long endTimestamp,
      String largeImageKey,
      String largeImageText,
      String largeImageUrl,
      String smallImageKey,
      String smallImageText,
      String smallImageUrl,
      String partyId,
      int partySize,
      int partyMax,
      PartyPrivacy partyPrivacy,
      String matchSecret,
      String joinSecret,
      String spectateSecret,
      JsonArray buttons,
      boolean instance
   ) {
      this.activityType = activityType;
      this.statusDisplayType = statusDisplayType;
      this.state = state;
      this.stateUrl = stateUrl;
      this.details = details;
      this.detailsUrl = detailsUrl;
      this.name = name;
      this.startTimestamp = startTimestamp;
      this.endTimestamp = endTimestamp;
      this.largeImageKey = largeImageKey;
      this.largeImageText = largeImageText;
      this.largeImageUrl = largeImageUrl;
      this.smallImageKey = smallImageKey;
      this.smallImageText = smallImageText;
      this.smallImageUrl = smallImageUrl;
      this.partyId = partyId;
      this.partySize = partySize;
      this.partyMax = partyMax;
      this.partyPrivacy = partyPrivacy;
      this.matchSecret = matchSecret;
      this.joinSecret = joinSecret;
      this.spectateSecret = spectateSecret;
      this.buttons = buttons;
      this.instance = instance;
   }

   public JsonObject toJson() {
      JsonObject timestamps = new JsonObject();
      JsonObject assets = new JsonObject();
      JsonObject party = new JsonObject();
      JsonObject secrets = new JsonObject();
      JsonObject activity = new JsonObject();
      if (this.startTimestamp > 0L) {
         timestamps.addProperty("start", this.startTimestamp);
         if (this.endTimestamp > this.startTimestamp) {
            timestamps.addProperty("end", this.endTimestamp);
         }
      }

      if (this.largeImageKey != null && !this.largeImageKey.isEmpty()) {
         assets.addProperty("large_image", this.largeImageKey);
         if (this.largeImageText != null && !this.largeImageText.isEmpty()) {
            assets.addProperty("large_text", this.largeImageText);
         }

         if (this.largeImageUrl != null && !this.largeImageUrl.isEmpty()) {
            assets.addProperty("large_url", this.largeImageUrl);
         }
      }

      if (this.smallImageKey != null && !this.smallImageKey.isEmpty()) {
         assets.addProperty("small_image", this.smallImageKey);
         if (this.smallImageText != null && !this.smallImageText.isEmpty()) {
            assets.addProperty("small_text", this.smallImageText);
         }

         if (this.smallImageUrl != null && !this.smallImageUrl.isEmpty()) {
            assets.addProperty("small_url", this.smallImageUrl);
         }
      }

      if (this.partyId != null && !this.partyId.isEmpty() || this.partySize > 0 && this.partyMax > 0) {
         if (this.partyId != null && !this.partyId.isEmpty()) {
            party.addProperty("id", this.partyId);
         }

         JsonArray size = new JsonArray();
         if (this.partySize > 0) {
            size.add(new JsonPrimitive(this.partySize));
            if (this.partyMax >= this.partySize) {
               size.add(new JsonPrimitive(this.partyMax));
            }
         }

         party.add("size", size);
         party.add("privacy", new JsonPrimitive(this.partyPrivacy.ordinal()));
      }

      if (this.joinSecret != null && !this.joinSecret.isEmpty()) {
         secrets.addProperty("join", this.joinSecret);
      }

      if (this.spectateSecret != null && !this.spectateSecret.isEmpty()) {
         secrets.addProperty("spectate", this.spectateSecret);
      }

      if (this.matchSecret != null && !this.matchSecret.isEmpty()) {
         secrets.addProperty("match", this.matchSecret);
      }

      activity.addProperty("type", this.activityType.ordinal());
      activity.addProperty("status_display_type", this.statusDisplayType.ordinal());
      if (this.state != null && !this.state.isEmpty()) {
         activity.addProperty("state", this.state);
         if (this.stateUrl != null && !this.stateUrl.isEmpty()) {
            activity.addProperty("state_url", this.stateUrl);
         }
      }

      if (this.details != null && !this.details.isEmpty()) {
         activity.addProperty("details", this.details);
         if (this.detailsUrl != null && !this.detailsUrl.isEmpty()) {
            activity.addProperty("details_url", this.detailsUrl);
         }
      }

      if (this.name != null && !this.name.isEmpty()) {
         activity.addProperty("name", this.name);
      }

      if (timestamps.has("start")) {
         activity.add("timestamps", timestamps);
      }

      if (assets.has("large_image")) {
         activity.add("assets", assets);
      }

      if (party.has("id")) {
         activity.add("party", party);
      }

      if (secrets.has("join") || secrets.has("spectate") || secrets.has("match")) {
         activity.add("secrets", secrets);
      }

      if (this.buttons != null && !this.buttons.isJsonNull() && this.buttons.size() >= 1 && this.buttons.size() <= 2) {
         activity.add("buttons", this.buttons);
      }

      activity.addProperty("instance", this.instance);
      return activity;
   }

   public String toDecodedJson(String encoding) {
      try {
         return new String(this.toJson().toString().getBytes(encoding));
      } catch (Exception ignored) {
         return this.toJson().toString();
      }
   }

   @Override
   public boolean equals(Object other) {
      return !(other instanceof RichPresence presence)
         ? false
         : this == presence
            || Objects.equals(this.activityType, presence.activityType)
               && Objects.equals(this.statusDisplayType, presence.statusDisplayType)
               && Objects.equals(this.state, presence.state)
               && Objects.equals(this.stateUrl, presence.stateUrl)
               && Objects.equals(this.details, presence.details)
               && Objects.equals(this.detailsUrl, presence.detailsUrl)
               && Objects.equals(this.name, presence.name)
               && Objects.equals(this.startTimestamp, presence.startTimestamp)
               && Objects.equals(this.endTimestamp, presence.endTimestamp)
               && Objects.equals(this.largeImageKey, presence.largeImageKey)
               && Objects.equals(this.largeImageText, presence.largeImageText)
               && Objects.equals(this.largeImageUrl, presence.largeImageUrl)
               && Objects.equals(this.smallImageKey, presence.smallImageKey)
               && Objects.equals(this.smallImageText, presence.smallImageText)
               && Objects.equals(this.smallImageUrl, presence.smallImageUrl)
               && Objects.equals(this.partyId, presence.partyId)
               && Objects.equals(this.partySize, presence.partySize)
               && Objects.equals(this.partyMax, presence.partyMax)
               && Objects.equals(this.partyPrivacy, presence.partyPrivacy)
               && Objects.equals(this.matchSecret, presence.matchSecret)
               && Objects.equals(this.joinSecret, presence.joinSecret)
               && Objects.equals(this.spectateSecret, presence.spectateSecret)
               && Objects.equals(this.buttons, presence.buttons)
               && Objects.equals(this.instance, presence.instance);
   }

   @Override
   public int hashCode() {
      return Objects.hash(
         this.activityType,
         this.statusDisplayType,
         this.state,
         this.stateUrl,
         this.details,
         this.detailsUrl,
         this.name,
         this.startTimestamp,
         this.endTimestamp,
         this.largeImageKey,
         this.largeImageText,
         this.largeImageUrl,
         this.smallImageKey,
         this.smallImageText,
         this.smallImageUrl,
         this.partyId,
         this.partySize,
         this.partyMax,
         this.partyPrivacy,
         this.matchSecret,
         this.joinSecret,
         this.spectateSecret,
         this.buttons,
         this.instance
      );
   }

   public static class Builder {
      private ActivityType activityType;
      private StatusDisplayType statusDisplayType;
      private String state;
      private String stateUrl;
      private String details;
      private String detailsUrl;
      private String name;
      private long startTimestamp;
      private long endTimestamp;
      private String largeImageKey;
      private String largeImageText;
      private String largeImageUrl;
      private String smallImageKey;
      private String smallImageText;
      private String smallImageUrl;
      private String partyId;
      private int partySize;
      private int partyMax;
      private PartyPrivacy partyPrivacy;
      private String matchSecret;
      private String joinSecret;
      private String spectateSecret;
      private JsonArray buttons;
      private boolean instance;

      public RichPresence build() {
         return new RichPresence(
            this.activityType,
            this.statusDisplayType,
            this.state,
            this.stateUrl,
            this.details,
            this.detailsUrl,
            this.name,
            this.startTimestamp,
            this.endTimestamp,
            this.largeImageKey,
            this.largeImageText,
            this.largeImageUrl,
            this.smallImageKey,
            this.smallImageText,
            this.smallImageUrl,
            this.partyId,
            this.partySize,
            this.partyMax,
            this.partyPrivacy,
            this.matchSecret,
            this.joinSecret,
            this.spectateSecret,
            this.buttons,
            this.instance
         );
      }

      public RichPresence.Builder setActivityType(ActivityType activityType) {
         this.activityType = activityType;
         return this;
      }

      public RichPresence.Builder setStatusDisplayType(StatusDisplayType statusDisplayType) {
         this.statusDisplayType = statusDisplayType;
         return this;
      }

      public RichPresence.Builder setState(String state) {
         this.state = state;
         return this;
      }

      public RichPresence.Builder setStateUrl(String stateUrl) {
         this.stateUrl = stateUrl;
         return this;
      }

      public RichPresence.Builder setDetails(String details) {
         this.details = details;
         return this;
      }

      public RichPresence.Builder setDetailsUrl(String detailsUrl) {
         this.detailsUrl = detailsUrl;
         return this;
      }

      public RichPresence.Builder setName(String name) {
         this.name = name;
         return this;
      }

      public RichPresence.Builder setStartTimestamp(long startTimestamp) {
         this.startTimestamp = startTimestamp;
         return this;
      }

      public RichPresence.Builder setEndTimestamp(long endTimestamp) {
         this.endTimestamp = endTimestamp;
         return this;
      }

      public RichPresence.Builder setLargeImage(String key, String text, String url) {
         this.largeImageKey = key;
         this.largeImageText = text;
         this.largeImageUrl = url;
         return this;
      }

      public RichPresence.Builder setLargeImageWithTooltip(String key, String text) {
         return this.setLargeImage(key, text, null);
      }

      public RichPresence.Builder setLargeImageWithUrl(String key, String url) {
         return this.setLargeImage(key, null, url);
      }

      public RichPresence.Builder setLargeImage(String key) {
         return this.setLargeImage(key, null, null);
      }

      public RichPresence.Builder setSmallImage(String key, String text, String url) {
         this.smallImageKey = key;
         this.smallImageText = text;
         this.smallImageUrl = url;
         return this;
      }

      public RichPresence.Builder setSmallImageWithTooltip(String key, String text) {
         return this.setSmallImage(key, text, null);
      }

      public RichPresence.Builder setSmallImageWithUrl(String key, String url) {
         return this.setSmallImage(key, null, url);
      }

      public RichPresence.Builder setSmallImage(String key) {
         return this.setSmallImage(key, null, null);
      }

      public RichPresence.Builder setParty(String id, int size, int max, PartyPrivacy privacy) {
         this.partyId = id;
         this.partySize = size;
         this.partyMax = max;
         this.partyPrivacy = privacy;
         return this;
      }

      public RichPresence.Builder setMatchSecret(String matchSecret) {
         this.matchSecret = matchSecret;
         return this;
      }

      public RichPresence.Builder setJoinSecret(String joinSecret) {
         this.joinSecret = joinSecret;
         return this;
      }

      public RichPresence.Builder setSpectateSecret(String spectateSecret) {
         this.spectateSecret = spectateSecret;
         return this;
      }

      public RichPresence.Builder setButtons(JsonArray buttons) {
         this.buttons = buttons;
         return this;
      }

      public RichPresence.Builder setInstance(boolean instance) {
         this.instance = instance;
         return this;
      }
   }
}
