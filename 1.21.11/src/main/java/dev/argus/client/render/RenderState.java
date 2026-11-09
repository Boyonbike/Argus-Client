package dev.argus.client.render;

public interface RenderState {
   default RenderState then(RenderState next) {
      return entering -> {
         this.apply(entering);
         next.apply(entering);
      };
   }

   static RenderState combine(RenderState... states) {
      return entering -> {
         for (RenderState state : states) {
            state.apply(entering);
         }
      };
   }

   void apply(boolean var1);
}
