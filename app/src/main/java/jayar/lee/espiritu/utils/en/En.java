package jayar.lee.espiritu.utils.en;

public class En {
    private En1 method;

    public void setMethod(En1 method) {
        this.method = method;
    }

    public String encryptString(String string, int key) {
        return this.method.encryptString(string, key);
    }
}
