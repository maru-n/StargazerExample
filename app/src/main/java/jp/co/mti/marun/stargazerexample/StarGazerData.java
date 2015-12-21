package jp.co.mti.marun.stargazerexample;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by maruyama_n on 2015/12/18.
 */
public class StarGazerData extends NavigationData{
    public final String rawDataString;

    public int markerId;
    public boolean isDeadZone = false;

    private static final Pattern DataPattern = Pattern.compile("~\\^I([0-9]+)\\|([\\+\\-][0-9]+\\.?[0-9]+)\\|([\\+\\-][0-9]+\\.?[0-9]+)\\|([\\+\\-][0-9]+\\.?[0-9]+)\\|([0-9]+\\.?[0-9]+)`");
    private static final Pattern DeadZonePattern = Pattern.compile("~\\*DeadZone`");

    public StarGazerData(String rawData) throws Exception {
        this.time = System.currentTimeMillis();
        this.rawDataString = rawData;
        Matcher m = DataPattern.matcher(rawData);
        if (m.find()) {
            this.markerId = Integer.parseInt(m.group(1));
            this.angle = Float.parseFloat(m.group(2));
            this.x = (float) (Float.parseFloat(m.group(3)) * 0.01);
            this.y = (float) (Float.parseFloat(m.group(4)) * 0.01);
            this.z = (float) (Float.parseFloat(m.group(5)) * 0.01);
        } else if (DeadZonePattern.matcher(rawData).find()) {
            this.isDeadZone = true;
        } else{
            throw new Exception("Invalid data format.");
        }
    }

    public String toXYString() {
        if (this.isDeadZone) {
            return String.format("time:%d DeadZone", this.time);
        } else {
            return String.format("time:%d x:%.4f  y:%.4f", this.time, this.x, this.y);
        }
    }

    public String toLogString() {
        if (this.isDeadZone) {
            return String.format("# %d DeadZone", this.time);
        } else {
            return String.format("%d %d %.2f %.4f %.4f %.4f", this.time, this.markerId, this.angle, this.x, this.y, this.z);
        }
    }


}