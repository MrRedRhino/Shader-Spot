CREATE TABLE IF NOT EXISTS shaders
(
    id                    text      not null primary key,
    slug                  text      not null,
    name                  text      not null,
    last_updated          timestamp not null,
    scraped_at            timestamp not null,
    latest_version        text      not null,
    download_url          text      not null,
    images_rendered_at    timestamp,
    images_render_version text
)