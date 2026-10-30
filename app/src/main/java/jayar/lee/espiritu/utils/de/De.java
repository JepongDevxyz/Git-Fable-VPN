package jayar.lee.espiritu.utils.de;

public class De {
    private De1 method;

    public void setMethod(De1 method) {
        this.method = method;
    }

    public String decryptString(String string, int key) {
        return this.method.decryptString(string, key);
    }
}
