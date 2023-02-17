package com.haoyoung.ashare.admin.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.haoyoung.ashare.admin.api.dto.AppSmsDTO;
import com.haoyoung.ashare.admin.api.dto.UserInfo;
import com.haoyoung.ashare.admin.api.entity.SysUser;
import com.haoyoung.ashare.admin.service.AppService;
import com.haoyoung.ashare.admin.service.SysUserService;
import com.haoyoung.ashare.common.core.exception.ErrorCodes;
import com.haoyoung.ashare.common.core.util.MsgUtils;
import com.haoyoung.ashare.common.core.util.R;
import com.haoyoung.ashare.common.security.annotation.Inner;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * @author lengleng
 * @date 2021/9/16 移动端登录
 */
@RestController
@AllArgsConstructor
@RequestMapping("/app")
@Tag(name = "移动端登录模块")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AppController {

	private final AppService appService;

	private final SysUserService userService;

	/**
	 * 发送手机验证码
	 * @param sms 请求手机对象
	 * @return code
	 */
	@Inner(value = false)
	@PostMapping("/sms")
	public R<Boolean> sendSmsCode(@Valid @RequestBody AppSmsDTO sms) {
		return appService.sendSmsCode(sms);
	}

	/**
	 * 获取指定用户全部信息
	 * @param phone 手机号
	 * @return 用户信息
	 */
	@Inner
	@GetMapping("/info/{phone}")
	public R<UserInfo> infoByMobile(@PathVariable String phone) {
		SysUser user = userService.getOne(Wrappers.<SysUser>query().lambda().eq(SysUser::getPhone, phone));
		if (user == null) {
			return R.failed(MsgUtils.getMessage(ErrorCodes.SYS_USER_USERINFO_EMPTY, phone));
		}
		return R.ok(userService.getUserInfo(user));
	}

}
