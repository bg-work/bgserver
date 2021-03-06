/*
 *
 *  *
 *  *  * Copyright (c) Shanghai Xing Ye, Co. Ltd.
 *  *  * https://bg.work
 *  *  *
 *  *  * GNU Lesser General Public License Usage
 *  *  * Alternatively, this file may be used under the terms of the GNU Lesser
 *  *  * General Public License version 3 as published by the Free Software
 *  *  * Foundation and appearing in the file LICENSE.txt included in the
 *  *  * project of this file. Please review the following information to
 *  *  * ensure the GNU Lesser General Public License version 3 requirements
 *  *  * will be met: https://www.gnu.org/licenses/lgpl-3.0.html.
 *  *
 *
 */

package work.bg.server.core.spring.boot.model

import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession

 interface  ModelWebContext{
    val request: HttpServletRequest
        get() {
            var ra= RequestContextHolder.getRequestAttributes() as ServletRequestAttributes
            return ra.request
        }

    val response: HttpServletResponse
        get() {
            var ra= RequestContextHolder.getRequestAttributes() as ServletRequestAttributes
            return ra.response
        }

     val Session: HttpSession
        get() {
           return this.request.getSession(true)
        }
}