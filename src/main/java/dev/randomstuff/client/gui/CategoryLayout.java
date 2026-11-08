package dev.randomstuff.client.gui;

/** Places category windows in columns that fit the scaled viewport. */
final class CategoryLayout {
    private CategoryLayout() {}

    static int[][] arrange(int viewportWidth, int[] heights) {
        int columns = Math.max(1, (viewportWidth - 20 + 8) / (112 + 8));
        columns = Math.min(columns, Math.max(1, heights.length));
        int[] bottoms = new int[columns];
        java.util.Arrays.fill(bottoms, 64);
        int[][] positions = new int[heights.length][2];
        for (int i = 0; i < heights.length; i++) {
            int column = 0;
            for (int j = 1; j < columns; j++) {
                if (bottoms[j] < bottoms[column]) column = j;
            }
            positions[i][0] = 10 + column * (112 + 8);
            positions[i][1] = bottoms[column];
            bottoms[column] += heights[i] + 8;
        }
        return positions;
    }
}
