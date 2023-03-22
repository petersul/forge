package forge.adventure;

import com.badlogic.gdx.math.Vector2;

import java.util.ArrayDeque;

public class PreviousLocation {
    private final int maximumStored;
    private final ArrayDeque<Vector2> locations = new ArrayDeque<>();

    public PreviousLocation(int max) {
        maximumStored = max;
    }

    // Returns true if we evicted
    public boolean add(Vector2 location) {
        boolean evicting = locations.size() >= maximumStored;
        if (evicting) {
            do {
                locations.pop();
            } while(locations.size() >= maximumStored);
        }
        locations.add(location);

        return evicting;
    }

    // We don't need these if we inherit from ArrayDeque which I think is preferred
    // Some delegated functions
    public Vector2 peek() {
        return locations.peek();
    }

    public int size() {
        return locations.size();
    }
}
