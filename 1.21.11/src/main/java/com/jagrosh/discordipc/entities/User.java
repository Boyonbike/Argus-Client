package com.jagrosh.discordipc.entities;

public class User {
   private final String username;
   private final String nickname;
   private final String discriminator;
   private final long id;
   private final String avatar;

   public User(String username, String nickname, String discriminator, long id, String avatar) {
      this.username = username;
      this.nickname = nickname;
      this.discriminator = discriminator;
      this.id = id;
      this.avatar = avatar;
   }

   public String getName() {
      return this.username;
   }

   public String getNickname() {
      return this.nickname;
   }

   public String getEffectiveName() {
      return this.nickname == null ? this.username : this.nickname;
   }

   public String getDiscriminator() {
      return this.discriminator;
   }

   public long getIdLong() {
      return this.id;
   }

   public String getId() {
      return Long.toString(this.id);
   }

   public String getAvatarId() {
      return this.avatar;
   }

   public String getAvatarUrl() {
      return this.getAvatarId() == null
         ? null
         : "https://cdn.discordapp.com/avatars/" + this.getId() + "/" + this.getAvatarId() + (this.getAvatarId().startsWith("a_") ? ".gif" : ".png");
   }

   public String getDefaultAvatarId() {
      int avatarIndex;
      if (this.getDiscriminator().equals("0")) {
         avatarIndex = ((int)this.getIdLong() >> 22) % 6;
      } else {
         avatarIndex = Integer.parseInt(this.getDiscriminator()) % 5;
      }

      return User.DefaultAvatar.values()[avatarIndex].toString();
   }

   public String getDefaultAvatarUrl() {
      return "https://discord.com/assets/" + this.getDefaultAvatarId() + ".png";
   }

   public String getEffectiveAvatarUrl() {
      return this.getAvatarUrl() == null ? this.getDefaultAvatarUrl() : this.getAvatarUrl();
   }

   public boolean isBot() {
      return false;
   }

   public String getAsMention() {
      return "<@" + this.id + ">";
   }

   @Override
   public boolean equals(Object other) {
      return !(other instanceof User user) ? false : this == user || this.id == user.id;
   }

   @Override
   public int hashCode() {
      return Long.hashCode(this.id);
   }

   @Override
   public String toString() {
      return "U:" + this.getName() + "(" + this.id + ")";
   }

   public enum DefaultAvatar {
      BLURPLE("6debd47ed13483642cf09e832ed0bc1b"),
      GREY("322c936a8c8be1b803cd94861bdfa868"),
      GREEN("dd4dbc0016779df1378e7812eabaa04d"),
      ORANGE("0e291f67c9274a1abdddeb3fd919cbaa"),
      RED("1cbd08c76f8af6dddce02c5138971129"),
      PINK("1b3106e166c99cc64682");

      private final String text;

      DefaultAvatar(String text) {
         this.text = text;
      }

      @Override
      public String toString() {
         return this.text;
      }
   }
}
