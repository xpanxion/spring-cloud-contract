/*
 *  Copyright 2013-2017 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.cloud.contract.verifier.builder

import org.junit.Rule
import org.springframework.boot.test.rule.OutputCapture
import org.springframework.cloud.contract.spec.Contract
import org.springframework.cloud.contract.verifier.config.ContractVerifierConfigProperties
import org.springframework.cloud.contract.verifier.dsl.WireMockStubVerifier
import org.springframework.cloud.contract.verifier.util.SyntaxChecker
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

class MockMvcMethodBodyBuilderWithMatchersSpec extends Specification implements WireMockStubVerifier {

	@Rule OutputCapture outputCapture = new OutputCapture()

	@Shared ContractVerifierConfigProperties properties = new ContractVerifierConfigProperties(
			assertJsonSize: true
	)

	@Issue('#185')
	def "should allow to set dynamic values via stub / test matchers for [#methodBuilderName]"() {
		given:
		//tag::matchers[]
			Contract contractDsl = Contract.make {
				request {
					method 'GET'
					urlPath '/get'
					body([
							duck: 123,
							alpha: "abc",
							number: 123,
							aBoolean: true,
							date: "2017-01-01",
							dateTime: "2017-01-01T01:23:45",
							time: "01:02:34",
							valueWithoutAMatcher: "foo",
							valueWithTypeMatch: "string",
							key: [
									'complex.key' : 'foo'
							]
					])
					stubMatchers {
						jsonPath('$.duck', byRegex("[0-9]{3}"))
						jsonPath('$.duck', byEquality())
						jsonPath('$.alpha', byRegex(onlyAlphaUnicode()))
						jsonPath('$.alpha', byEquality())
						jsonPath('$.number', byRegex(number()))
						jsonPath('$.aBoolean', byRegex(anyBoolean()))
						jsonPath('$.date', byDate())
						jsonPath('$.dateTime', byTimestamp())
						jsonPath('$.time', byTime())
						jsonPath("\$.['key'].['complex.key']", byEquality())
					}
					headers {
						contentType(applicationJson())
					}
				}
				response {
					status 200
					body([
							duck: 123,
							alpha: "abc",
							number: 123,
							aBoolean: true,
							date: "2017-01-01",
							dateTime: "2017-01-01T01:23:45",
							time: "01:02:34",
							valueWithoutAMatcher: "foo",
							valueWithTypeMatch: "string",
							valueWithMin: [
								1,2,3
							],
							valueWithMax: [
								1,2,3
							],
							valueWithMinMax: [
								1,2,3
							],
							valueWithMinEmpty: [],
							valueWithMaxEmpty: [],
							key: [
							        'complex.key' : 'foo'
							]
					])
					testMatchers {
						// asserts the jsonpath value against manual regex
						jsonPath('$.duck', byRegex("[0-9]{3}"))
						// asserts the jsonpath value against the provided value
						jsonPath('$.duck', byEquality())
						// asserts the jsonpath value against some default regex
						jsonPath('$.alpha', byRegex(onlyAlphaUnicode()))
						jsonPath('$.alpha', byEquality())
						jsonPath('$.number', byRegex(number()))
						jsonPath('$.aBoolean', byRegex(anyBoolean()))
						// asserts vs inbuilt time related regex
						jsonPath('$.date', byDate())
						jsonPath('$.dateTime', byTimestamp())
						jsonPath('$.time', byTime())
						// asserts that the resulting type is the same as in response body
						jsonPath('$.valueWithTypeMatch', byType())
						jsonPath('$.valueWithMin', byType {
							// results in verification of size of array (min 1)
							minOccurrence(1)
						})
						jsonPath('$.valueWithMax', byType {
							// results in verification of size of array (max 3)
							maxOccurrence(3)
						})
						jsonPath('$.valueWithMinMax', byType {
							// results in verification of size of array (min 1 & max 3)
							minOccurrence(1)
							maxOccurrence(3)
						})
						jsonPath('$.valueWithMinEmpty', byType {
							// results in verification of size of array (min 0)
							minOccurrence(0)
						})
						jsonPath('$.valueWithMaxEmpty', byType {
							// results in verification of size of array (max 0)
							maxOccurrence(0)
						})
						// will execute a method `assertThatValueIsANumber`
						jsonPath('$.duck', byCommand('assertThatValueIsANumber($it)'))
						jsonPath("\$.['key'].['complex.key']", byEquality())
					}
					headers {
						contentType(applicationJson())
					}
				}
			}
			//end::matchers[]
			MethodBodyBuilder builder = methodBuilder(contractDsl)
			BlockBuilder blockBuilder = new BlockBuilder(" ")
		when:
			builder.appendTo(blockBuilder)
			def test = blockBuilder.toString()
		then:
			test.contains('assertThat(parsedJson.read("' + rootElement + '.duck", String.class)).matches("[0-9]{3}")')
			test.contains('assertThat(parsedJson.read("' + rootElement + '.duck", Integer.class)).isEqualTo(123)')
			test.contains('assertThat(parsedJson.read("' + rootElement + '.alpha", String.class)).matches("[\\\\p{L}]*")')
			test.contains('assertThat(parsedJson.read("' + rootElement + '.alpha", String.class)).isEqualTo("abc")')
			test.contains('assertThat(parsedJson.read("' + rootElement + '.number", String.class)).matches("-?\\\\d*(\\\\.\\\\d+)?")')
			test.contains('assertThat(parsedJson.read("' + rootElement + '.aBoolean", String.class)).matches("(true|false)")')
			test.contains('assertThat(parsedJson.read("' + rootElement + '.date", String.class)).matches("(\\\\d\\\\d\\\\d\\\\d)-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])")')
			test.contains('assertThat(parsedJson.read("' + rootElement + '.dateTime", String.class)).matches("([0-9]{4})-(1[0-2]|0[1-9])-(3[01]|0[1-9]|[12][0-9])T(2[0-3]|[01][0-9]):([0-5][0-9]):([0-5][0-9])")')
			test.contains('assertThat(parsedJson.read("' + rootElement + '.time", String.class)).matches("(2[0-3]|[01][0-9]):([0-5][0-9]):([0-5][0-9])")')
			test.contains('assertThat((Object) parsedJson.read("' + rootElement + '.valueWithTypeMatch")).isInstanceOf(java.lang.String.class)')
			test.contains('assertThat((Object) parsedJson.read("' + rootElement + '.valueWithMin")).isInstanceOf(java.util.List.class)')
			test.contains('assertThat((java.lang.Iterable) parsedJson.read("' + rootElement + '.valueWithMin", java.util.Collection.class)).hasSizeGreaterThanOrEqualTo(1)')
			test.contains('assertThat((Object) parsedJson.read("' + rootElement + '.valueWithMax")).isInstanceOf(java.util.List.class)')
			test.contains('assertThat((java.lang.Iterable) parsedJson.read("' + rootElement + '.valueWithMax", java.util.Collection.class)).hasSizeLessThanOrEqualTo(3)')
			test.contains('assertThat((Object) parsedJson.read("' + rootElement + '.valueWithMinMax")).isInstanceOf(java.util.List.class)')
			test.contains('assertThat((java.lang.Iterable) parsedJson.read("' + rootElement + '.valueWithMinMax", java.util.Collection.class)).hasSizeBetween(1, 3)')
			test.contains('assertThat((Object) parsedJson.read("' + rootElement + '.valueWithMinEmpty")).isInstanceOf(java.util.List.class)')
			test.contains('assertThat((java.lang.Iterable) parsedJson.read("' + rootElement + '.valueWithMinEmpty", java.util.Collection.class)).hasSizeGreaterThanOrEqualTo(0)')
			test.contains('assertThat((Object) parsedJson.read("' + rootElement + '.valueWithMaxEmpty")).isInstanceOf(java.util.List.class)')
			test.contains('assertThat((java.lang.Iterable) parsedJson.read("' + rootElement + '.valueWithMaxEmpty", java.util.Collection.class)).hasSizeLessThanOrEqualTo(0)')
			test.contains('assertThatValueIsANumber(parsedJson.read("' + rootElement + '.duck")')
			test.contains('assertThat(parsedJson.read("' + rootElement + '''.['key'].['complex.key']", String.class)).isEqualTo("foo")''')
			!test.contains('cursor')
		and:
			try {
				SyntaxChecker.tryToCompileWithoutCompileStatic(methodBuilderName, blockBuilder.toString())
			} catch (ClassFormatError classFormatError) {
				String output = outputCapture.toString()
				output.contains('error: cannot find symbol')
				output.contains('assertThatValueIsANumber(parsedJson.read("$.duck"));')
			}
		where:
			methodBuilderName                                    | methodBuilder                                                                               | rootElement
			"MockMvcSpockMethodBuilder"                          | { Contract dsl -> new MockMvcSpockMethodRequestProcessingBodyBuilder(dsl, properties) }     | '\\$'
			"MockMvcJUnitMethodBuilder"                          | { Contract dsl -> new MockMvcJUnitMethodBodyBuilder(dsl, properties) }                      | '$'
			"JaxRsClientSpockMethodRequestProcessingBodyBuilder" | { Contract dsl -> new JaxRsClientSpockMethodRequestProcessingBodyBuilder(dsl, properties) } | '\\$'
			"JaxRsClientJUnitMethodBodyBuilder"                  | { Contract dsl -> new JaxRsClientJUnitMethodBodyBuilder(dsl, properties) }                  | '$'
	}

	@Issue('#217')
	def "should allow complex matchers for [#methodBuilderName]"() {
		given:
			Contract contractDsl = Contract.make {
				request {
					method 'GET'
					url 'person'
				}
				response {
					status 200
					body([
							"firstName": "Jane",
							"lastName": "Doe",
							"isAlive": true,
							"address": [
									"postalCode": "98101",
							],
							"phoneNumbers": [
									[
											"type": "home",
											"number": "999 999-9999",
									]
							],
							"gender": [
									"type": "female",
							],
							"children": [
									[
											"firstName": "Kid",
											"age": 55,
									]
							],
					])
					testMatchers {
						jsonPath('$.phoneNumbers', byType {
							minOccurrence(0)				// min occurrence of 1
							maxOccurrence(4)				// max occurrence of 3
						})
						jsonPath('$.phoneNumbers[*].number', byRegex("^[0-9]{3} [0-9]{3}-[0-9]{4}\$"))
						jsonPath('$..number', byRegex("^[0-9]{3} [0-9]{3}-[0-9]{4}\$"))
					}
					headers {
						contentType('application/json')
					}
				}
			}
			MethodBodyBuilder builder = methodBuilder(contractDsl)
			BlockBuilder blockBuilder = new BlockBuilder(" ")
		when:
			builder.appendTo(blockBuilder)
			def test = blockBuilder.toString()
		then:
			test.contains('assertThat((java.lang.Iterable) parsedJson.read("' + rootElement + '.phoneNumbers[*].number", java.util.Collection.class)).allElementsMatch("^[0-9]{3} [0-9]{3}-[0-9]{4}' + rootElement + '")')
			test.contains('assertThat((java.lang.Iterable) parsedJson.read("' + rootElement + '..number", java.util.Collection.class)).allElementsMatch("^[0-9]{3} [0-9]{3}-[0-9]{4}' + rootElement + '")')
			!test.contains('cursor')
		and:
			try {
				SyntaxChecker.tryToCompileWithoutCompileStatic(methodBuilderName, blockBuilder.toString())
			} catch(NoClassDefFoundError error) {
				// that's actually expected since we're creating an anonymous class
			}
		where:
			methodBuilderName                                    | methodBuilder                                                                               | rootElement
			"MockMvcSpockMethodBuilder"                          | { Contract dsl -> new MockMvcSpockMethodRequestProcessingBodyBuilder(dsl, properties) }     | '\\$'
			"MockMvcJUnitMethodBuilder"                          | { Contract dsl -> new MockMvcJUnitMethodBodyBuilder(dsl, properties) }                      | '$'
			"JaxRsClientSpockMethodRequestProcessingBodyBuilder" | { Contract dsl -> new JaxRsClientSpockMethodRequestProcessingBodyBuilder(dsl, properties) } | '\\$'
			"JaxRsClientJUnitMethodBodyBuilder"                  | { Contract dsl -> new JaxRsClientJUnitMethodBodyBuilder(dsl, properties) }                  | '$'
	}


	@Issue('#217')
	def "should use the flattened assertions when jsonpath contains [*] for [#methodBuilderName]"() {
		given:
		Contract contractDsl = Contract.make {
			request {
				method 'GET'
				url 'person'
			}
			response {
				status 200
				body([
						"phoneNumbers": [
								number: "foo"
						]
				])
				testMatchers {
					jsonPath('$.phoneNumbers[*].number', byType {
						minOccurrence(0)
						maxOccurrence(4)
					})
					jsonPath('$.phoneNumbers[*].number', byType {
						minOccurrence(0)
					})
					jsonPath('$.phoneNumbers[*].number', byType {
						maxOccurrence(4)
					})
				}
			}
		}
			MethodBodyBuilder builder = methodBuilder(contractDsl)
			BlockBuilder blockBuilder = new BlockBuilder(" ")
		when:
			builder.appendTo(blockBuilder)
			def test = blockBuilder.toString()
		then:
			test.contains('assertThat((java.lang.Iterable) parsedJson.read("' + rootElement + '.phoneNumbers[*].number", java.util.Collection.class)).hasFlattenedSizeBetween(0, 4)')
			test.contains('assertThat((java.lang.Iterable) parsedJson.read("' + rootElement + '.phoneNumbers[*].number", java.util.Collection.class)).hasFlattenedSizeGreaterThanOrEqualTo(0)')
			test.contains('assertThat((java.lang.Iterable) parsedJson.read("' + rootElement + '.phoneNumbers[*].number", java.util.Collection.class)).hasFlattenedSizeLessThanOrEqualTo(4)')
			!test.contains('cursor')
		and:
			try {
				SyntaxChecker.tryToCompileWithoutCompileStatic(methodBuilderName, blockBuilder.toString())
			} catch(NoClassDefFoundError error) {
				// that's actually expected since we're creating an anonymous class
			}
		where:
			methodBuilderName                                    | methodBuilder                                                                               | rootElement
			"MockMvcSpockMethodBuilder"                          | { Contract dsl -> new MockMvcSpockMethodRequestProcessingBodyBuilder(dsl, properties) }     | '\\$'
			"MockMvcJUnitMethodBuilder"                          | { Contract dsl -> new MockMvcJUnitMethodBodyBuilder(dsl, properties) }                      | '$'
			"JaxRsClientSpockMethodRequestProcessingBodyBuilder" | { Contract dsl -> new JaxRsClientSpockMethodRequestProcessingBodyBuilder(dsl, properties) } | '\\$'
			"JaxRsClientJUnitMethodBodyBuilder"                  | { Contract dsl -> new JaxRsClientJUnitMethodBodyBuilder(dsl, properties) }                  | '$'
	}

	@Issue('#217')
	def "should allow matcher with command to execute [#methodBuilderName]"() {
		given:
			Contract contractDsl = Contract.make {
				request {
					method 'GET'
					url 'person'
				}
				response {
					status 200
					body([
							"phoneNumbers": [
							        number: "foo"
							]
					])
					testMatchers {
						jsonPath('$.phoneNumbers[*].number', byCommand('foo($it)'))
					}
				}
			}
			MethodBodyBuilder builder = methodBuilder(contractDsl)
			BlockBuilder blockBuilder = new BlockBuilder(" ")
		when:
			builder.appendTo(blockBuilder)
			def test = blockBuilder.toString()
		then:
			test.contains('foo(parsedJson.read("' + rootElement + '.phoneNumbers[*].number")')
		where:
			methodBuilderName                                    | methodBuilder                                                                               | rootElement
			"MockMvcSpockMethodBuilder"                          | { Contract dsl -> new MockMvcSpockMethodRequestProcessingBodyBuilder(dsl, properties) }     | '\\$'
			"MockMvcJUnitMethodBuilder"                          | { Contract dsl -> new MockMvcJUnitMethodBodyBuilder(dsl, properties) }                      | '$'
			"JaxRsClientSpockMethodRequestProcessingBodyBuilder" | { Contract dsl -> new JaxRsClientSpockMethodRequestProcessingBodyBuilder(dsl, properties) } | '\\$'
			"JaxRsClientJUnitMethodBodyBuilder"                  | { Contract dsl -> new JaxRsClientJUnitMethodBodyBuilder(dsl, properties) }                  | '$'
	}

	@Issue('#217')
	def "should throw an exception when command to execute references a non existing entry in the body [#methodBuilderName]"() {
		given:
			Contract contractDsl = Contract.make {
				request {
					method 'GET'
					url 'person'
				}
				response {
					status 200
					body([
							"phoneNumbers": [
							        number: "foo"
							]
					])
					testMatchers {
						jsonPath('$.nonExistingPhoneNumbers[*].number', byCommand('foo($it)'))
					}
				}
			}
			MethodBodyBuilder builder = methodBuilder(contractDsl)
			BlockBuilder blockBuilder = new BlockBuilder(" ")
		when:
			builder.appendTo(blockBuilder)
		then:
			IllegalStateException e = thrown(IllegalStateException)
			e.message.contains("Entry for the provided JSON path <\$.nonExistingPhoneNumbers[*].number> doesn't exist in the body")
		where:
			methodBuilderName                                    | methodBuilder                                                                               | rootElement
			"MockMvcSpockMethodBuilder"                          | { Contract dsl -> new MockMvcSpockMethodRequestProcessingBodyBuilder(dsl, properties) }     | '\\$'
			"MockMvcJUnitMethodBuilder"                          | { Contract dsl -> new MockMvcJUnitMethodBodyBuilder(dsl, properties) }                      | '$'
			"JaxRsClientSpockMethodRequestProcessingBodyBuilder" | { Contract dsl -> new JaxRsClientSpockMethodRequestProcessingBodyBuilder(dsl, properties) } | '\\$'
			"JaxRsClientJUnitMethodBodyBuilder"                  | { Contract dsl -> new JaxRsClientJUnitMethodBodyBuilder(dsl, properties) }                  | '$'
	}

	@Issue("#229")
	def "should work for matchers and body with json array[#methodBuilderName]"() {
		given:
			Contract contractDsl = Contract.make {
				request {
					method 'GET'
					url '/api/v1/xxxx'
					body(12000)
				}
				response {
					status 200
					body ([[
								   [ access_token: '123']
						   ]])
					headers {
						contentType(applicationJson())
					}
					testMatchers {
						jsonPath('''$[0][0].access_token''', byEquality())
					}
				}
			}
			MethodBodyBuilder builder = methodBuilder(contractDsl)
			BlockBuilder blockBuilder = new BlockBuilder(" ")
		and:
			builder.appendTo(blockBuilder)
			String test = blockBuilder.toString()
		when:
			SyntaxChecker.tryToCompile(methodBuilderName, test)
		then:
			test.contains('assertThat(parsedJson.read("' + rootElement + '[0][0].access_token", String.class)).isEqualTo("123")')
		where:
			methodBuilderName                                    | methodBuilder                                                                               | rootElement
			"MockMvcSpockMethodBuilder"                          | { Contract dsl -> new MockMvcSpockMethodRequestProcessingBodyBuilder(dsl, properties) }     | '\\$'
			"MockMvcJUnitMethodBuilder"                          | { Contract dsl -> new MockMvcJUnitMethodBodyBuilder(dsl, properties) }                      | '$'
			"JaxRsClientSpockMethodRequestProcessingBodyBuilder" | { Contract dsl -> new JaxRsClientSpockMethodRequestProcessingBodyBuilder(dsl, properties) } | '\\$'
			"JaxRsClientJUnitMethodBodyBuilder"                  | { Contract dsl -> new JaxRsClientJUnitMethodBodyBuilder(dsl, properties) }                  | '$'
	}

	@Issue("#391")
	def "should work for matchers and body with multiline string for [#methodBuilderName]"() {
		given:
			Contract contractDsl = Contract.make {
				request {
					name "ISSUE 391"
					method 'GET'
					urlPath '/item/factsheet?size=2&page=1'
					headers { header "accept", "application/...json" }
				}
				response {
					status 200
					body("""
							{
								"items": [
									{
										"id": "35309",
										"title": "lorem ipsum"
									}
								]
							}
						""")
					testMatchers {
						jsonPath('$.items[*].id', byRegex(nonBlank()))
						jsonPath('$.items[*].title', byRegex(nonBlank()))
						jsonPath('$.items[*]', byType { minOccurrence(2); maxOccurrence(2) })
					}
					headers {header "content-type", "application/...json;charset=UTF-8"}
				}
			}
			MethodBodyBuilder builder = methodBuilder(contractDsl)
			BlockBuilder blockBuilder = new BlockBuilder(" ")
		and:
			builder.appendTo(blockBuilder)
			String test = blockBuilder.toString()
		when:
			SyntaxChecker.tryToCompile(methodBuilderName, test)
		then:
			!test.contains('''assertThatJson(parsedJson).array("['items']").isEmpty()''')
		where:
			methodBuilderName                                    | methodBuilder
			"MockMvcSpockMethodBuilder"                          | { Contract dsl -> new MockMvcSpockMethodRequestProcessingBodyBuilder(dsl, properties) }
			"MockMvcJUnitMethodBuilder"                          | { Contract dsl -> new MockMvcJUnitMethodBodyBuilder(dsl, properties) }
			"JaxRsClientSpockMethodRequestProcessingBodyBuilder" | { Contract dsl -> new JaxRsClientSpockMethodRequestProcessingBodyBuilder(dsl, properties) }
			"JaxRsClientJUnitMethodBodyBuilder"                  | { Contract dsl -> new JaxRsClientJUnitMethodBodyBuilder(dsl, properties) }
	}

	@Issue("#391")
	def "should work for matchers and body with multiline string with map body for [#methodBuilderName]"() {
		given:
			Contract contractDsl = Contract.make {
				request {
					name "ISSUE 391"
					method 'GET'
					urlPath '/item/factsheet?size=2&page=1'
					headers { header "accept", "application/...json" }
				}
				response {
					status 200
					body([
							"items": [
									"id"   : "35309",
									"title": "lorem ipsum"
							]
					])
					testMatchers {
						jsonPath('$.items[*].id', byRegex(nonBlank()))
						jsonPath('$.items[*].title', byRegex(nonBlank()))
						jsonPath('$.items[*]', byType { minOccurrence(2); maxOccurrence(2) })
					}
					headers {header "content-type", "application/...json;charset=UTF-8"}
				}
			}
			MethodBodyBuilder builder = methodBuilder(contractDsl)
			BlockBuilder blockBuilder = new BlockBuilder(" ")
		and:
			builder.appendTo(blockBuilder)
			String test = blockBuilder.toString()
		when:
			SyntaxChecker.tryToCompile(methodBuilderName, test)
		then:
			!test.contains('''assertThatJson(parsedJson).array("['items']").isEmpty()''')
		where:
			methodBuilderName                                    | methodBuilder
			"MockMvcSpockMethodBuilder"                          | { Contract dsl -> new MockMvcSpockMethodRequestProcessingBodyBuilder(dsl, properties) }
			"MockMvcJUnitMethodBuilder"                          | { Contract dsl -> new MockMvcJUnitMethodBodyBuilder(dsl, properties) }
			"JaxRsClientSpockMethodRequestProcessingBodyBuilder" | { Contract dsl -> new JaxRsClientSpockMethodRequestProcessingBodyBuilder(dsl, properties) }
			"JaxRsClientJUnitMethodBodyBuilder"                  | { Contract dsl -> new JaxRsClientJUnitMethodBodyBuilder(dsl, properties) }
	}

}
