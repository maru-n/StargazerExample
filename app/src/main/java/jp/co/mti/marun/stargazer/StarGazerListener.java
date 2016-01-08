package jp.co.mti.marun.stargazer;

/**
 * Created by maruyama_n on 2015/12/21.
 */
public interface StarGazerListener {
    void onNewData(StarGazerManager sm, final StarGazerData data);
    void onError(StarGazerManager sm, final StarGazerException e);
}
