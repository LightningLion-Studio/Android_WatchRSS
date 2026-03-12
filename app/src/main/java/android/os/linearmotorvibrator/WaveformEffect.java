package android.os.linearmotorvibrator;

public class WaveformEffect {
    private WaveformEffect() {
    }

    public static class Builder {
        public Builder() {
        }

        public Builder setEffectType(int type) {
            return this;
        }

        public Builder setEffectStrength(int strength) {
            return this;
        }

        public Builder setEffectLoop(boolean loop) {
            return this;
        }

        public WaveformEffect build() {
            return new WaveformEffect();
        }
    }
}
