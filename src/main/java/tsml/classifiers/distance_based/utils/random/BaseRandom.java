package tsml.classifiers.distance_based.utils.random;

import java.util.Random;
import org.junit.Assert;

/**
 * Purpose: // todo - docs - type the purpose of the code here
 * <p>
 * Contributors: goastler
 */
public class BaseRandom implements RandomSource {

    private Random random = new Random();

    public BaseRandom(int seed) {
        this(new Random(seed));
    }

    public BaseRandom(final Random random) {
        this.random = random;
    }

    @Override
    public void setRandom(final Random random) {
        Assert.assertNotNull(random);
        this.random = random;
    }

    @Override
    public Random getRandom() {
        return random;
    }
}
