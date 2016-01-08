package jp.co.mti.marun.stargazer;

public class NavigationDataDebug extends NavigationData {

    private float p = 10;
    private float r = 28;
    private float b = (float) (8.0/3.0);
    private float scale = (float) 0.2;

    public NavigationDataDebug() {
        this.time = System.currentTimeMillis();
        this.x = (float) Math.random();
        this.y = (float) Math.random();
        this.z = (float) Math.random();
    }

    public NavigationDataDebug(NavigationData previousData) {
        previousData.x /= scale;
        previousData.y /= scale;
        previousData.z /= scale;

        this.time = System.currentTimeMillis();
        float dt = (float) ((this.time - previousData.time)*0.00005);
        this.x = previousData.x + dt * (-p*previousData.x + p*previousData.y);
        this.y = previousData.y + dt * (-previousData.x*previousData.z + r*previousData.x - previousData.y);
        this.z = previousData.z + dt * (previousData.x*previousData.y - b*previousData.z);

        this.x *= scale;
        this.y *= scale;
        this.z *= scale;
        previousData.x *= scale;
        previousData.y *= scale;
        previousData.z *= scale;
    }
}
