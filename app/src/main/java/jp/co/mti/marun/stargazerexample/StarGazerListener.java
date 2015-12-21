package jp.co.mti.marun.stargazerexample;

/**
 * Created by maruyama_n on 2015/12/21.
 */
public interface StarGazerListener {
    void onNewData(final StarGazerData data);
    void onError(final StarGazerException e);
}
