package com.fenix.mobilemcp.mcp.oauth

import com.fenix.mobilemcp.data.repository.OAuthClientRepository
import com.fenix.mobilemcp.geo.GeoIpResolver

/**
 * Groups the OAuth collaborators passed into [com.fenix.mobilemcp.mcp.McpServer]
 * so its constructor stays within detekt's `LongParameterList` threshold (no `@Suppress` needed).
 */
class OAuthServerDeps(
    val jwtTokenService: JwtTokenService,
    val oauthClientRepository: OAuthClientRepository,
    val authorizationCodeStore: AuthorizationCodeStore,
    val approvalCoordinator: OAuthApprovalCoordinator,
    val geoIpResolver: GeoIpResolver,
)
