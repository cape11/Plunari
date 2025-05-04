package org.isogame.map;

/**
 * A simple implementation of Simplex noise for 2D heightmap generation
 */
public class SimplexNoise {
    // Constants for improved simplex noise
    private static final double SQRT3 = Math.sqrt(3.0);
    private static final double SQRT5 = Math.sqrt(5.0);

    // Permutation table
    private int[] perm = new int[512];
    private int seed;

    public SimplexNoise(int seed) {
        this.seed = seed;
        initPermutation();
    }

    private void initPermutation() {
        // Initialize the permutation with a simple hash
        for (int i = 0; i < 256; i++) {
            perm[i] = i;
        }

        // Shuffle based on the seed
        for (int i = 255; i > 0; i--) {
            int j = (seed ^ i) & 255;
            int temp = perm[i];
            perm[i] = perm[j];
            perm[j] = temp;
        }

        // Duplicate to avoid overflow
        for (int i = 0; i < 256; i++) {
            perm[i + 256] = perm[i];
        }
    }

    // 2D simplex noise
    public double noise(double x, double y) {
        // Find the unit grid cell containing the point
        int X = fastfloor(x);
        int Y = fastfloor(y);

        // Get relative coordinates in the cell
        x = x - X;
        y = y - Y;

        // Wrap to 0..255 for permutation
        X = X & 255;
        Y = Y & 255;

        // Calculate noise contributions from the three corners
        double n0, n1, n2;

        // Skew the input space to determine which simplex we're in
        double F2 = 0.5 * (SQRT3 - 1.0);
        double s = (x + y) * F2;
        int i = fastfloor(x + s);
        int j = fastfloor(y + s);

        double G2 = (3.0 - SQRT3) / 6.0;
        double t = (i + j) * G2;
        double X0 = i - t;
        double Y0 = j - t;
        double x0 = x - X0;
        double y0 = y - Y0;

        // Determine which simplex we're in
        int i1, j1;
        if (x0 > y0) {
            i1 = 1; j1 = 0; // lower triangle
        } else {
            i1 = 0; j1 = 1; // upper triangle
        }

        // Offsets for the other two corners
        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double y2 = y0 - 1.0 + 2.0 * G2;

        // Calculate gradient indices
        int gi0 = perm[X + perm[Y]] % 12;
        int gi1 = perm[X + i1 + perm[Y + j1]] % 12;
        int gi2 = perm[X + 1 + perm[Y + 1]] % 12;

        // Calculate noise contributions from each corner
        double t0 = 0.5 - x0*x0 - y0*y0;
        if (t0 < 0) {
            n0 = 0.0;
        } else {
            t0 *= t0;
            n0 = t0 * t0 * grad(gi0, x0, y0);
        }

        double t1 = 0.5 - x1*x1 - y1*y1;
        if (t1 < 0) {
            n1 = 0.0;
        } else {
            t1 *= t1;
            n1 = t1 * t1 * grad(gi1, x1, y1);
        }

        double t2 = 0.5 - x2*x2 - y2*y2;
        if (t2 < 0) {
            n2 = 0.0;
        } else {
            t2 *= t2;
            n2 = t2 * t2 * grad(gi2, x2, y2);
        }

        // Add contributions from each corner to get the final noise value
        // The result is scaled to return values in the interval [-1,1]
        return 70.0 * (n0 + n1 + n2);
    }

    // This is a helper method used by noise()
    private int fastfloor(double x) {
        int xi = (int)x;
        return x < xi ? xi - 1 : xi;
    }

    // Gradient function
    private double grad(int hash, double x, double y) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : (h == 12 || h == 14 ? x : 0);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    // Generate octaved noise for more natural terrain
    public double octaveNoise(double x, double y, int octaves, double persistence) {
        double total = 0;
        double frequency = 1;
        double amplitude = 1;
        double maxValue = 0;

        for (int i = 0; i < octaves; i++) {
            total += noise(x * frequency, y * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= 2;
        }

        return total / maxValue;
    }

    // Generate terrain with different features
    public double generateTerrain(double x, double y) {
        // Base terrain
        double base = octaveNoise(x, y, 6, 0.5);

        // Mountains (sharper noise)
        double mountains = Math.pow(octaveNoise(x * 0.5, y * 0.5, 4, 0.6), 3) * 0.5;

        // Plains (smoother)
        double plains = octaveNoise(x * 2, y * 2, 2, 0.1) * 0.1;

        // Combine different features
        double combined = base * 0.5 + mountains + plains;

        // Normalize to range [-1, 1]
        return Math.max(-1, Math.min(1, combined));
    }
}