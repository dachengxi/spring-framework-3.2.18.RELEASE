/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.scheduling.aspectj;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.scheduling.annotation.AbstractAsyncConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @Configuration} class that registers the Spring infrastructure beans necessary
 * to enable AspectJ-based asynchronous method execution.
 *
 * @author Chris Beams
 * @since 3.1
 * @see EnableAsync
 * @see org.springframework.scheduling.annotation.AsyncConfigurationSelector
 * @see org.springframework.scheduling.annotation.ProxyAsyncConfiguration
 * 注册一个基于ASPECTJ的异步方法执行的Bean
 */
@Configuration
public class AspectJAsyncConfiguration extends AbstractAsyncConfiguration {

	/**
	 * 注册一个内部使用的Bean，AnnotationAsyncExecutionAspect
	 * @return
	 */
	@Bean(name = AnnotationConfigUtils.ASYNC_EXECUTION_ASPECT_BEAN_NAME)
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public AnnotationAsyncExecutionAspect asyncAdvisor() {
		AnnotationAsyncExecutionAspect asyncAspect = AnnotationAsyncExecutionAspect.aspectOf();
		if (this.executor != null) {
			asyncAspect.setExecutor(this.executor);
		}
		return asyncAspect;
	}

}
