package com.emreuzum.argame.game;

public final class CreatureModelRegistry {

    private CreatureModelRegistry() {
    }

    public static CreatureModelConfig getConfigForCreature(int creatureId) {
        switch (creatureId) {
            case 1:
                return new CreatureModelConfig("models/monsters/totemaw.glb", 0.11f, 0.0f);
            case 2:
                return new CreatureModelConfig("models/monsters/skulljaw.glb", 0.11f, 0.0f);
            case 3:
                return new CreatureModelConfig("models/monsters/monkroose.glb", 0.11f, 0.0f);
            case 4:
                return new CreatureModelConfig("models/monsters/tidefin.glb", 0.12f, 0.0f);
            case 5:
                return new CreatureModelConfig("models/monsters/thornback.glb", 0.10f, 0.0f);
            case 6:
                return new CreatureModelConfig("models/monsters/cinderfiend.glb", 0.10f, 0.0f);
            case 7:
                return new CreatureModelConfig("models/monsters/frostfiend.glb", 0.10f, 0.0f);
            case 8:
                return new CreatureModelConfig("models/monsters/bramblebun.glb", 0.12f, 0.0f);
            case 9:
                return new CreatureModelConfig("models/monsters/skychirp.glb", 0.12f, 0.0f);
            default:
                return null;
        }
    }

    public static class CreatureModelConfig {
        public final String assetPath;
        public final float scale;
        public final float verticalOffset;

        public CreatureModelConfig(String assetPath, float scale, float verticalOffset) {
            this.assetPath = assetPath;
            this.scale = scale;
            this.verticalOffset = verticalOffset;
        }
    }
}
