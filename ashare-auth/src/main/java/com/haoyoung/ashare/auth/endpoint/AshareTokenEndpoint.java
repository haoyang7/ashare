/*
 * Copyright (c) 2020 ashare4cloud Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.haoyoung.ashare.auth.endpoint;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.TemporalAccessorUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoyoung.ashare.admin.api.entity.SysOauthClientDetails;
import com.haoyoung.ashare.admin.api.feign.RemoteClientDetailsService;
import com.haoyoung.ashare.admin.api.vo.TokenVo;
import com.haoyoung.ashare.auth.support.handler.AshareAuthenticationFailureEventHandler;
import com.haoyoung.ashare.common.core.constant.CacheConstants;
import com.haoyoung.ashare.common.core.constant.CommonConstants;
import com.haoyoung.ashare.common.core.util.R;
import com.haoyoung.ashare.common.core.util.RetOps;
import com.haoyoung.ashare.common.core.util.SpringContextHolder;
import com.haoyoung.ashare.common.security.annotation.Inner;
import com.haoyoung.ashare.common.security.util.OAuth2EndpointUtils;
import com.haoyoung.ashare.common.security.util.OAuth2ErrorCodesExpand;
import com.haoyoung.ashare.common.security.util.OAuthClientException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author lengleng
 * @date 2019/2/1 ??????token??????
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/token")
public class AshareTokenEndpoint {

	private final HttpMessageConverter<OAuth2AccessTokenResponse> accessTokenHttpResponseConverter = new OAuth2AccessTokenResponseHttpMessageConverter();

	private final AuthenticationFailureHandler authenticationFailureHandler = new AshareAuthenticationFailureEventHandler();

	private final OAuth2AuthorizationService authorizationService;

	private final RemoteClientDetailsService clientDetailsService;

	private final RedisTemplate<String, Object> redisTemplate;

	private final CacheManager cacheManager;

	/**
	 * ????????????
	 * @param modelAndView
	 * @param error ?????????????????????????????????????????????
	 * @return ModelAndView
	 */
	@GetMapping("/login")
	public ModelAndView require(ModelAndView modelAndView, @RequestParam(required = false) String error) {
		modelAndView.setViewName("ftl/login");
		modelAndView.addObject("error", error);
		return modelAndView;
	}

	@GetMapping("/confirm_access")
	public ModelAndView confirm(Principal principal, ModelAndView modelAndView,
			@RequestParam(OAuth2ParameterNames.CLIENT_ID) String clientId,
			@RequestParam(OAuth2ParameterNames.SCOPE) String scope,
			@RequestParam(OAuth2ParameterNames.STATE) String state) {
		SysOauthClientDetails clientDetails = RetOps.of(clientDetailsService.getClientDetailsById(clientId)).getData()
				.orElseThrow(() -> new OAuthClientException("clientId ?????????"));

		Set<String> authorizedScopes = StringUtils.commaDelimitedListToSet(clientDetails.getScope());
		modelAndView.addObject("clientId", clientId);
		modelAndView.addObject("state", state);
		modelAndView.addObject("scopeList", authorizedScopes);
		modelAndView.addObject("principalName", principal.getName());
		modelAndView.setViewName("ftl/confirm");
		return modelAndView;
	}

	/**
	 * ???????????????token
	 * @param authHeader Authorization
	 */
	@DeleteMapping("/logout")
	public R<Boolean> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
		if (StrUtil.isBlank(authHeader)) {
			return R.ok();
		}

		String tokenValue = authHeader.replace(OAuth2AccessToken.TokenType.BEARER.getValue(), StrUtil.EMPTY).trim();
		return removeToken(tokenValue);
	}

	/**
	 * ??????token
	 * @param token ??????
	 */
	@SneakyThrows
	@GetMapping("/check_token")
	public void checkToken(String token, HttpServletResponse response, HttpServletRequest request) {
		ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(response);

		if (StrUtil.isBlank(token)) {
			httpResponse.setStatusCode(HttpStatus.UNAUTHORIZED);
			this.authenticationFailureHandler.onAuthenticationFailure(request, response,
					new InvalidBearerTokenException(OAuth2ErrorCodesExpand.TOKEN_MISSING));
			return;
		}
		OAuth2Authorization authorization = authorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);

		// ????????????????????? ??????401
		if (authorization == null || authorization.getAccessToken() == null) {
			this.authenticationFailureHandler.onAuthenticationFailure(request, response,
					new InvalidBearerTokenException(OAuth2ErrorCodesExpand.INVALID_BEARER_TOKEN));
			return;
		}

		Map<String, Object> claims = authorization.getAccessToken().getClaims();
		OAuth2AccessTokenResponse sendAccessTokenResponse = OAuth2EndpointUtils.sendAccessTokenResponse(authorization,
				claims);
		this.accessTokenHttpResponseConverter.write(sendAccessTokenResponse, MediaType.APPLICATION_JSON, httpResponse);
	}

	/**
	 * ??????????????????
	 * @param token token
	 */
	@Inner
	@DeleteMapping("/{token}")
	public R<Boolean> removeToken(@PathVariable("token") String token) {
		OAuth2Authorization authorization = authorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
		if (authorization == null) {
			return R.ok();
		}

		OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getAccessToken();
		if (accessToken == null || StrUtil.isBlank(accessToken.getToken().getTokenValue())) {
			return R.ok();
		}
		// ??????????????????
		cacheManager.getCache(CacheConstants.USER_DETAILS).evict(authorization.getPrincipalName());
		// ??????access token
		authorizationService.remove(authorization);
		// ????????????????????????????????????????????????
		SpringContextHolder.publishEvent(new LogoutSuccessEvent(new PreAuthenticatedAuthenticationToken(
				authorization.getPrincipalName(), authorization.getRegisteredClientId())));
		return R.ok();
	}

	/**
	 * ??????token
	 * @param params ????????????
	 * @return
	 */
	@Inner
	@PostMapping("/page")
	public R<Page> tokenList(@RequestBody Map<String, Object> params) {
		// ????????????????????????????????????
		String key = String.format("%s::*", CacheConstants.PROJECT_OAUTH_ACCESS);
		int current = MapUtil.getInt(params, CommonConstants.CURRENT);
		int size = MapUtil.getInt(params, CommonConstants.SIZE);
		Set<String> keys = redisTemplate.keys(key);
		List<String> pages = keys.stream().skip((current - 1) * size).limit(size).collect(Collectors.toList());
		Page result = new Page(current, size);

		List<TokenVo> tokenVoList = redisTemplate.opsForValue().multiGet(pages).stream().map(obj -> {
			OAuth2Authorization authorization = (OAuth2Authorization) obj;
			TokenVo tokenVo = new TokenVo();
			tokenVo.setClientId(authorization.getRegisteredClientId());
			tokenVo.setId(authorization.getId());
			tokenVo.setUsername(authorization.getPrincipalName());
			OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getAccessToken();
			tokenVo.setAccessToken(accessToken.getToken().getTokenValue());

			String expiresAt = TemporalAccessorUtil.format(accessToken.getToken().getExpiresAt(),
					DatePattern.NORM_DATETIME_PATTERN);
			tokenVo.setExpiresAt(expiresAt);

			String issuedAt = TemporalAccessorUtil.format(accessToken.getToken().getIssuedAt(),
					DatePattern.NORM_DATETIME_PATTERN);
			tokenVo.setIssuedAt(issuedAt);
			return tokenVo;
		}).collect(Collectors.toList());
		result.setRecords(tokenVoList);
		result.setTotal(keys.size());
		return R.ok(result);
	}

}
