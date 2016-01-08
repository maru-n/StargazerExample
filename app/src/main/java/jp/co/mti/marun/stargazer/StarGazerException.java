package jp.co.mti.marun.stargazer;

/**
 * Created by maruyama_n on 2015/12/21.
 */
public class StarGazerException extends Exception {
    public StarGazerException(String s) {
        super(s);
    }

    public StarGazerException(String s, Exception e) {
        super(s, e);
    }
}
