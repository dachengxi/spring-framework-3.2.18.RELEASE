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

package org.springframework.cache.config;

import org.w3c.dom.Element;

import org.springframework.aop.config.AopNamespaceUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.parsing.CompositeComponentDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.cache.interceptor.BeanFactoryCacheOperationSourceAdvisor;
import org.springframework.cache.interceptor.CacheInterceptor;
import org.springframework.context.annotation.AnnotationConfigUtils;

/**
 * {@link org.springframework.beans.factory.xml.BeanDefinitionParser}
 * implementation that allows users to easily configure all the
 * infrastructure beans required to enable annotation-driven cache
 * demarcation.
 *
 * <p>By default, all proxies are created as JDK proxies. This may cause
 * some problems if you are injecting objects as concrete classes rather
 * than interfaces. To overcome this restriction you can set the
 * '{@code proxy-target-class}' attribute to '{@code true}', which will
 * result in class-based proxies being created.
 *
 * @author Costin Leau
 * @since 3.1
 * 解析<cache:annotation-driven/>
 */
class AnnotationDrivenCacheBeanDefinitionParser implements BeanDefinitionParser {

	/**
	 * Parses the '{@code <cache:annotation-driven>}' tag. Will
	 * {@link AopNamespaceUtils#registerAutoProxyCreatorIfNecessary
	 * register an AutoProxyCreator} with the container as necessary.
	 */
	public BeanDefinition parse(Element element, ParserContext parserContext) {
		String mode = element.getAttribute("mode");
		if ("aspectj".equals(mode)) {
			// mode="aspectj"
			registerCacheAspect(element, parserContext);
		}
		else {
			// mode="proxy"
			// 基于自动代理
			AopAutoProxyConfigurer.configureAutoProxyCreator(element, parserContext);
		}

		return null;
	}

	private static void parseCacheManagerProperty(Element element, BeanDefinition def) {
		def.getPropertyValues().add("cacheManager",
				new RuntimeBeanReference(CacheNamespaceHandler.extractCacheManager(element)));
	}

	/**
	 * Registers a
	 * <pre class="code">
	 * <bean id="cacheAspect" class="org.springframework.cache.aspectj.AnnotationCacheAspect" factory-method="aspectOf">
	 *   <property name="cacheManager" ref="cacheManager"/>
	 *   <property name="keyGenerator" ref="keyGenerator"/>
	 * </bean>
	 * </pre>
	 */
	private void registerCacheAspect(Element element, ParserContext parserContext) {
		if (!parserContext.getRegistry().containsBeanDefinition(AnnotationConfigUtils.CACHE_ASPECT_BEAN_NAME)) {
			RootBeanDefinition def = new RootBeanDefinition();
			def.setBeanClassName(AnnotationConfigUtils.CACHE_ASPECT_CLASS_NAME);
			def.setFactoryMethodName("aspectOf");
			parseCacheManagerProperty(element, def);
			CacheNamespaceHandler.parseKeyGenerator(element, def);
			parserContext.registerBeanComponent(new BeanComponentDefinition(def, AnnotationConfigUtils.CACHE_ASPECT_BEAN_NAME));
		}
	}


	/**
	 * Inner class to just introduce an AOP framework dependency when actually in proxy mode.
	 */
	private static class AopAutoProxyConfigurer {

		public static void configureAutoProxyCreator(Element element, ParserContext parserContext) {
			// 注册一个自动代理创建器：InfrastructureAdvisorAutoProxyCreator，
			// 这个自动代理创建器用来创建Spring内部的一些自动代理，比如缓存的代理
			AopNamespaceUtils.registerAutoProxyCreatorIfNecessary(parserContext, element);

			if (!parserContext.getRegistry().containsBeanDefinition(AnnotationConfigUtils.CACHE_ADVISOR_BEAN_NAME)) {
				Object eleSource = parserContext.extractSource(element);

				// Create the CacheOperationSource definition.
				/**
				 * 创建一个CacheOperationSource，类型是AnnotationCacheOperationSource
				 * AnnotationCacheOperationSource在实例化的时候会添加一个SpringCacheAnnotationParser到annotationParsers集合中去
				 * SpringCacheAnnotationParser用来解析缓存相关注解
				 */
				RootBeanDefinition sourceDef = new RootBeanDefinition("org.springframework.cache.annotation.AnnotationCacheOperationSource");
				sourceDef.setSource(eleSource);
				sourceDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
				String sourceName = parserContext.getReaderContext().registerWithGeneratedName(sourceDef);

				// Create the CacheInterceptor definition.
				// 创建CacheInterceptor，缓存方法执行的时候，会被该拦截器拦截，这里面做缓存相关操作
				RootBeanDefinition interceptorDef = new RootBeanDefinition(CacheInterceptor.class);
				interceptorDef.setSource(eleSource);
				interceptorDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
				// 解析cacheManager
				parseCacheManagerProperty(element, interceptorDef);
				// keyGenerator
				CacheNamespaceHandler.parseKeyGenerator(element, interceptorDef);
				interceptorDef.getPropertyValues().add("cacheOperationSources", new RuntimeBeanReference(sourceName));
				String interceptorName = parserContext.getReaderContext().registerWithGeneratedName(interceptorDef);

				// Create the CacheAdvisor definition.
				// 创建一个BeanFactoryCacheOperationSourceAdvisor，Advisor会在aop查找advisor的时候被用到
				RootBeanDefinition advisorDef = new RootBeanDefinition(BeanFactoryCacheOperationSourceAdvisor.class);
				advisorDef.setSource(eleSource);
				advisorDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
				advisorDef.getPropertyValues().add("cacheOperationSource", new RuntimeBeanReference(sourceName));
				advisorDef.getPropertyValues().add("adviceBeanName", interceptorName);
				if (element.hasAttribute("order")) {
					advisorDef.getPropertyValues().add("order", element.getAttribute("order"));
				}
				parserContext.getRegistry().registerBeanDefinition(AnnotationConfigUtils.CACHE_ADVISOR_BEAN_NAME, advisorDef);

				CompositeComponentDefinition compositeDef = new CompositeComponentDefinition(element.getTagName(),
						eleSource);
				compositeDef.addNestedComponent(new BeanComponentDefinition(sourceDef, sourceName));
				compositeDef.addNestedComponent(new BeanComponentDefinition(interceptorDef, interceptorName));
				compositeDef.addNestedComponent(new BeanComponentDefinition(advisorDef, AnnotationConfigUtils.CACHE_ADVISOR_BEAN_NAME));
				parserContext.registerComponent(compositeDef);
			}
		}
	}

}
