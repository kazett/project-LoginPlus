package kazet.loginplus;

import java.util.UUID;

/** Satu akun = satu UUID. Akun (password) beda dengan nama character ingame. */
public class Account {

    public final UUID uuid;
    public String accountName;
    public String hash;
    public String salt;
    /** IP yang disimpan buat auto login (null = tidak disimpan). */
    public String savedIp;

    public boolean hasCharacter;
    public String name = "";
    public String lastName = "";
    public String gender = "";
    public String nationality = "";
    public int day = 1;
    public int month = 1;
    public int year = 2000;

    /** ID map buat KTP (-1 = belum ada). */
    public int mapId = -1;

    public Account(UUID uuid, String accountName) {
        this.uuid = uuid;
        this.accountName = accountName;
    }

    public String fullName() {
        return (name + " " + lastName).trim();
    }

    public String dobString() {
        return String.format("%02d-%02d-%04d", day, month, year);
    }
}
