package com.kwri.auto.mcrecruits.data;

/**
 * List of users used in repository.
 */
public enum User {
    TEAMSBRAV0_MC_MCA("MCATeamsBravo", "teams/teamBravo/test_bravo_pass", "password", "test_bravo_pass");
    private final String login;
    private final String secretPath;
    private final String secretKey;
    private final String password;

    User(final String login, final String secretPath, final String secretKey, final String password) {
        this.login = login;
        this.secretPath = secretPath;
        this.secretKey = secretKey;
        this.password = password;
    }

    /**
     * Getter for user's login.
     *
     * @return {@link String} username
     */
    public String getLogin() {
        return login;
    }

    /**
     * Getter for path of user's password secret.
     *
     * @return {@link String} password key
     */
    public String getPasswordPath() {
        return secretPath;
    }

    /**
     * Getter for key of user's password secret
     *
     * @return {@link String} password key
     */
    public String getPasswordKey() {
        return secretKey;
    }

    /**
     * Getter for user's passwords.
     *
     * @return {@link String} passwords
     */
    public String getPassword() {
        return password + ".password";
    }
}
