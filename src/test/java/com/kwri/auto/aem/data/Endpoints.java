package com.kwri.auto.aem.data;

/**
 * List of end-points used in this repository.
 */
public enum Endpoints {
    LOGIN("login"),
    MEMBERS("members");

    private final String url;
    private String url2;
    private String url3;
    private String url4;

    Endpoints(final String url) {
        this.url = url;
    }

    Endpoints(final String url, final String url2) {
        this.url = url;
        this.url2 = url2;
    }

    Endpoints(final String url, final String url2, final String url3) {
        this.url = url;
        this.url2 = url2;
        this.url3 = url3;
    }

    Endpoints(final String url, final String url2, final String url3, final String url4) {
        this.url = url;
        this.url2 = url2;
        this.url3 = url3;
        this.url4 = url4;
    }

    /**
     * This method will return url for end-point based on amount of parameters.
     *
     * @param id list of parameters
     * @return {@link String} end-point url with parameters
     */
    public String getUrl(String... id) {
        if (id == null || id.length == 0) {
            return url;
        }

        StringBuilder builder = new StringBuilder(url);

        switch (id.length) {
            case 1:
                builder.append('/').append(id[0]);
                if (url2 != null) {
                    builder.append(url2);
                }
                break;

            case 2:
                builder.append('/').append(id[0])
                        .append(url2 != null ? url2 : "")
                        .append('/').append(id[1])
                        .append(url3 != null ? url3 : "");
                break;

            case 3:
                builder.append('/').append(id[0])
                        .append(url2 != null ? url2 : "")
                        .append('/').append(id[1])
                        .append(url3 != null ? url3 : "")
                        .append('/').append(id[2])
                        .append(url4 != null ? url4 : "");
                break;

            default:
                builder.append('/').append(id[0])
                        .append(url2 != null ? url2 : "")
                        .append('/').append(id[1])
                        .append(url3 != null ? url3 : "")
                        .append('/').append(id[2])
                        .append(url4 != null ? url4 : "")
                        .append('/').append(id[3]);
                break;
        }

        return builder.toString();
    }
}

