package jayar.lee.espiritu.config;

public interface SettingsConstants
{

    public static final String
            FrontQuery = "_FrontQuery",
            BackQuery = "_BackQuery",
            CONFIG_VERSION = "_CONFIG_VERSION",
            SERVER_TYPE = "7t_SERVER_TYPE",
            SERVER_KEY = "7sshServer",
            SERVER_PORT_KEY = "7sshPort",
            PROXY_IP_KEY = "7proxyRemoto",
            PROXY_PORTA_KEY = "7proxyRemotoPorta",
            USERNAME_KEY = "7sshUser",
            PASSWORD_KEY = "7sshPass",
            DIRECT_UDP_CONFIG_KEY = "_DIRECT_UDP_CONFIG_KEY",
            PAYLOAD_TYPE_KEY = "7mPAYLOAD_TYPE_KEY",
            CUSTOM_PAYLOAD_KEY = "7proxyPayload",
            SNI_HOST_KEY = "7ssl_sni_key",
            OpenVPN_CERT = "_OpenVPN_CERT",
            SERVER_POSITION = "7t_SERVER_POSITION",
            NETWORK_POSITION = "7t_NETWORK_POSITION";

    public static final int
            PAYLOAD_TYPE_HTTP_PROXY = 1,
            PAYLOAD_TYPE_SSL = 2,
            PAYLOAD_TYPE_SSL_PAYLOAD = 3,
            PAYLOAD_TYPE_SSL_PROXY = 4,
            PAYLOAD_TYPE_UDP = 5;


}
