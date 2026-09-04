package io.github.mamble;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * mstore の key-value ストアに対する読み書き。
 *
 * <p>値はすべて文字列として扱う。ストア側はバイト列としか見ていないので、
 * 数値も名前もここで文字列に均している。
 *
 * <p>実装を差し替えられるようインターフェースにしてある。テストは HTTP を張らない
 * 偽物を挿す。
 */
public interface KvClient {

    /** 値を取る。キーが無ければ空。 */
    Optional<String> get(String key) throws IOException;

    /** 値を書く。既にあれば上書きする。 */
    void put(String key, String value) throws IOException;

    /** 接頭辞に一致するキーを列挙する。値は含まない。 */
    List<String> keys(String prefix) throws IOException;

    /** 疎通を確かめる。到達できなければ例外を投げる。 */
    void ping() throws IOException;
}
