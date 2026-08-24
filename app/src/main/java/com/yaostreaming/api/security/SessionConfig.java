package com.yaostreaming.api.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

/**
 * Backs HttpSession with the SPRING_SESSION table (see
 * V5__create_spring_session_tables.sql) instead of each pod's own memory, so
 * any pod behind the load balancer can serve any request for an
 * already-signed-in user.
 *
 * spring-boot-session's autoconfiguration only wires the filter/cookie/
 * timeout plumbing around a SessionRepository bean that already exists — it
 * no longer creates one for you from a "store-type" property the way older
 * Spring Boot versions did. @EnableJdbcHttpSession is what actually
 * registers the JDBC-backed SessionRepository itself.
 */
@Configuration
@EnableJdbcHttpSession
public class SessionConfig {

}
