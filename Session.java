package kazet.loginplus;

/** Data sementara player yang belum login (state menu GUI). */
public class Session {

    public enum Screen { NONE, LOGIN, REGISTER, CHARACTER, NATIONALITY, INPUT }

    public enum Field { PASSWORD, CONFIRM, NAME, LAST }

    public Screen screen = Screen.NONE;
    public Screen returnTo = Screen.LOGIN;
    public Field inputField;

    public final long joinedAt = System.currentTimeMillis();

    /** opening = plugin lagi buka inventory sendiri (jangan dianggap "ditutup player"). */
    public boolean opening;
    /** ending = sesi selesai, jangan buka menu lagi. */
    public boolean ending;
    /** nunggu resource pack ke-load. */
    public boolean packWait;
    /** true kalau resource pack GUI aktif di client player ini. */
    public boolean textured;
    /** true kalau sudah lolos login (auto login by IP) tapi belum bikin character. */
    public boolean verified;

    public int attempts;

    // login / register
    public String password = "";
    public String confirm = "";
    public boolean saveLogin;

    // create character
    public String name = "";
    public String last = "";
    public String gender = null;
    public String nationality = null;
    public int day = 1;
    public int month = 1;
    public int year = 2000;
}
