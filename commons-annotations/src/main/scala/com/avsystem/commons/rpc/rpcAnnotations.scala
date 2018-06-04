package com.avsystem.commons
package rpc

import scala.annotation.StaticAnnotation

/**
  * For annotations applied on real RPC traits, their methods and parameters.
  */
trait RpcAnnotation extends StaticAnnotation

/**
  * For annotations applied on raw RPC traits, their methods and parameters.
  * They specify how real methods are matched against raw methods and real parameters against raw parameters.
  */
sealed trait RawRpcAnnotation extends StaticAnnotation
sealed trait RawMethodAnnotation extends RawRpcAnnotation
sealed trait RawParamAnnotation extends RawRpcAnnotation

/**
  * You can use this annotation on overloaded RPC methods to give them unique identifiers for RPC serialization.
  * You can also subclass this annotation provided that you always override the `name` parameter with another
  * constructor parameter.
  */
class rpcName(val name: String) extends RpcAnnotation

/**
  * Base trait for RPC tag annotations. Tagging gives more direct control over how real methods
  * and their parameters are matched against raw methods and their parameters.
  * For more information about method tagging, see documentation of [[methodTag]].
  * For more information about parameter tagging, see documentation of [[paramTag]].
  */
trait RpcTag extends RpcAnnotation

/**
  * Base trait for RPC arity annotations, [[single]], [[optional]] and [[multi]].
  * They are applied on raw parameters to tell the RPC macro engine how many real parameters the raw parameter
  * corresponds to. If no arity is specified, [[single]] is assumed by default.
  */
sealed trait RpcArity extends RawParamAnnotation

/**
  * When applied on raw parameter, specifies that this raw parameter must be matched by exactly one real parameter.
  * By default, [[single]] raw parameters are also [[verbatim]] which means that the real parameter must have
  * exactly the same type as the raw parameter.
  */
final class single extends RpcArity

/**
  * When applied on raw parameter, specifies that this raw parameter may be matched by a real parameter but this is
  * not required. Whether a real parameter matches an optional raw parameter is determined by its type and/or tag
  * (see [[paramTag]] for more information on param tagging).
  *
  * Raw parameters marked as [[optional]] must be typed as `Option[T]` (or `Opt[T]`, `OptArg[T]` or whatever type that
  * has an instance of `OptionLike`). By default, [[optional]] raw parameters are [[verbatim]] which means that the
  * option-wrapped type `T` must match exactly the type of real parameter.
  *
  * In the macro generated code that translates a raw call into a real call, when the raw parameter value is absent
  * (the `Option[T]` is empty) then real parameter's default value will be used as fallback. This allows introducing
  * new parameters into RPC interfaces without breaking backwards compatibility.
  */
final class optional extends RpcArity

/**
  * When applied on raw parameter, specifies that this raw parameter may be matched by arbitrary number of real
  * parameters whose values are typically [[encoded]] and collected into (or extracted from) raw parameter value.
  * The way real parameter values are collected and extracted depends on the type of raw parameter which must be
  * either:
  *
  * - an `Iterable[R]` or any subtype (e.g. `List[R]`)
  * - a `PartialFunction[String,R]` or any subtype (e.g. `Map[String,R]`)
  *
  * `R` denotes the type used to represent each real parameter value. Be default (unless [[verbatim]] is used) it means
  * that each real value will be encoded as `R` and decoded from `R` - see [[encoded]] for more information about
  * how parameters are encoded and decoded.
  *
  * If raw parameter is a `Coll <: Iterable[Raw]` then in order to collect real values into raw value, the macro engine
  * will search for an instance of `CanBuildFrom[Nothing,R,Coll]` and use it to build the raw value.
  * In order to extract real values from raw value, macro generated code will obtain an `Iterator[R]` from the raw value
  * and pull and possibly decode all the values. If the iterator is exhausted before all real values could be obtained
  * then default parameter values specified on real parameters (if any) will be used as fallback values instead of
  * failing.
  *
  * If raw parameter is an `IndexedSeq[Raw]` then extraction is optimized - instead of using an iterator,
  * values are extracted by index (potentially also falling back to default values of real parameters).
  *
  * Finally, when raw parameter is a `Mapping <: PartialFunction[String,R]` then not only values but also parameter
  * names are collected - the macro engine will search for an instance of `CanBuildFrom[Nothing,(String,R),Mapping]`
  * in order to collect the mapping. Parameter names may be adjusted with [[rpcName]] but must be unique in the
  * scope of a single raw parameter. In order to extract real values from mapping, macro generated code will
  * call `applyOrElse` on the raw value giving it the name of each real parameter and also falling back to possible
  * default parameter values, in case they are missing in the raw call.
  *
  * Note that when raw parameter is a name-value mapping, you can freely reorder real parameter without fear of
  * breaking backwards compatibility. You can also safely add new real parameters as long as you provide default
  * values for them.
  */
final class multi extends RpcArity

/**
  * Base trait for [[verbatim]] and [[encoded]]. These annotations can be applied either on a raw method or
  * raw parameter in order to specify how matching real method results or matching real parameter values are encoded
  * as raw values.
  * Currently there are two possible cases: [[verbatim]] (no encoding) and [[encoded]] (encoding using `AsRaw` and
  * `AsReal` typeclasses). By default, method return values and [[multi]] parameters are [[encoded]] while
  * [[single]] and [[optional]] parameters are [[verbatim]].
  * See documentation of [[verbatim]] and [[encoded]] for more details.
  */
sealed trait RpcEncoding extends RawMethodAnnotation with RawParamAnnotation

/**
  * When a raw parameter is annotated as [[encoded]], macro generated code will translate
  * between real parameter values and raw parameter values using implicit instances of `AsRaw[Raw,Real]`
  * and `AsReal[Raw,Real]` typeclasses. This annotation may also be applied on a method, but this would be
  * redundant since method results are encoded by default.
  *
  * Here's an example of raw RPC definition supposed to handle asynchronous (`Future`-based) calls that uses
  * `GenCodec` in order to encode and decode arguments and results as JSON strings.
  * It introduces its own wrapper class for JSON strings that has appropriate implicit instances of
  * `AsRaw` and `AsReal` (or `AsRealRaw` which serves as both `AsReal` and `AsRaw`).
  *
  * {{{
  * import com.avsystem.commons._
  * import com.avsystem.commons.rpc._
  * import com.avsystem.commons.serialization._
  * import com.avsystem.commons.serialization.json._
  *
  * case class Json(jsonStr: String)
  * object Json {
  *   private def readJson[T: GenCodec](json: Json): T =
  *     JsonStringInput.read[T](json.jsonStr)
  *
  *   private def writeJson[T: GenCodec](value: T): Json =
  *     Json(JsonStringOutput.write[T](value))
  *
  *   implicit def genCodecBasedJsonEncoding[T: GenCodec]: AsRealRaw[T,Json] =
  *     AsRealRaw.create[T,Json](readJson[T], writeJson[T])
  *
  *   // instead of using `mapNow`, this method can also take implicit ExecutionContext and just use `map`
  *   implicit def genCodecBasedFutureJsonEncoding[T: GenCodec]: AsRealRaw[Future[Json],Future[T]] =
  *     AsRealRaw.create[Future[T],Future[Json]](_.mapNow(readJson[T]), _.mapNow(writeJson[T]))
  * }
  *
  * trait AsyncRawRpc {
  *   def call(rpcName: String, @multi args: Map[String,Json]): Future[Json]
  * }
  * }}}
  *
  * If you don't want to introduce the wrapper `Json` class and use more raw type, e.g. plain `String` then you
  * can also do it by moving implicit instances of `AsReal` and `AsRaw` (or the joined `AsRealRaw`) into the
  * `implicits` object of raw RPC companion:
  *
  * {{{
  * trait AsyncRawRpc {
  *   def call(rpcName: String, @multi args: Map[String,String]): Future[String]
  * }
  * object AsyncRawRpc extends RawRpcCompanion[AsyncRawRpc] {
  *   private def readJson[T: GenCodec](json: String): T =
  *     JsonStringInput.read[T](json)
  *   private def writeJson[T: GenCodec](value: T): String =
  *     JsonStringOutput.write[T](value)
  *
  *   override object implicits {
  *     implicit def genCodecBasedJsonEncoding[T: GenCodec]: AsRealRaw[String,T] =
  *       AsRealRaw.create[String,T](readJson[T], writeJson[T])
  *     implicit def genCodecBasedFutureJsonEncoding[T: GenCodec]: AsRealRaw[Future[String],Future[T]] =
  *       AsRealRaw.create[Future[String],Future[T]](_.mapNow(readJson[T]), _.mapNow(writeJson[T]))
  *   }
  * }
  * }}}
  */
final class encoded extends RpcEncoding

/**
  * Turns off raw value encoding as specified by [[encoded]]. By default, [[single]] and [[optional]] raw parameters
  * are already [[verbatim]], so using [[verbatim]] only makes sense on [[multi]] raw parameters or
  * raw methods themselves, which means turning off encoding of method's result.
  *
  * When encoding is turned off, raw and real types must be exactly the same types. For example, the following raw RPC
  * definition will match only raw RPC traits whose methods take `Int`s as parameters and return `Double`s as values:
  *
  * {{{
  * trait VerbatimRawRpc {
  *   @verbatim def call(rpcName: String, @multi @verbatim args: Map[String,Int]): Double
  * }
  * }}}
  */
final class verbatim extends RpcEncoding

/**
  * Method tagging lets you have more explicit control over which raw methods can match which real methods.
  * Example:
  *
  * {{{
  * sealed trait RestMethod extends RpcTag
  * class GET extends RestMethod
  * class POST extends RestMethod
  *
  * @methodTag[RestMethod,GET]
  * trait RestRawRpc {
  *   @tagged[GET] def get(name: String, @multi args: Map[String,Json]): Future[Json]
  *   @tagged[POST] def post(name: String, @multi args: Map[String,Json]): Future[Json]
  * }
  * }}}
  *
  * In the example above, we created a hierarchy of annotations rooted at `RestMethod` which can be used
  * on real methods in order to explicitly tell the RPC macro which raw methods can match it.
  * We also specify `GET` as the default tag that will be assumed for real methods without any tag annotation.
  * Then, using `@tagged` we specify that the raw `get` method may only match real methods annotated as `GET`
  * while `post` raw method may only match real methods annotated as `POST`.
  * Raw methods not annotated with `@tagged` have no limitations and may still match any real methods.
  *
  * NOTE: The example above assumes there is a `Json` type defined with appropriate encodings -
  * see [[encoded]] for more details on parameter and method result encoding.
  *
  * An example of real RPC for `RestRawRpc`:
  *
  * {{{
  * trait SomeRestApi {
  *   def getUser(id: UserId): Future[User]
  *   @POST def saveUser(user: User): Future[Unit]
  * }
  * object SomeRestApi {
  *   implicit val asRealRaw: AsRealRaw[RestRawRpc,SomeRestApi] = AsRealRaw.materializeForRpc
  * }
  * }}}
  *
  * @tparam BaseTag    base type for tags that can be used on real RPC methods
  * @tparam DefaultTag the default tag type used for real methods not explicitly tagged - if you don't want to
  *                    introduce any specific default tag, just use the same type as for `BaseTag`
  */
final class methodTag[BaseTag <: RpcTag, DefaultTag <: BaseTag] extends RawRpcAnnotation

/**
  * Parameter tagging lets you have more explicit control over which raw parameters can match which real
  * parameters. This way you can have some of the parameters annotated in order to treat them differently, e.g.
  * they may be [[verbatim]], encoded in a different way or collected to a different raw container (e.g.
  * `Map[String,Raw]` vs `List[Raw]` - see [[multi]] for more details).
  *
  * Example:
  * {{{
  * sealed trait RestParam extends RpcTag
  * class Body extends RestParam
  * class Url extends RestParam
  * class Path extends RestParam
  *
  * @paramTag[RestParam,Body]
  * trait RestRawRpc {
  *   def get(name: String,
  *     @multi @verbatim @tagged[Path] pathParams: List[String],
  *     @multi @verbatim @tagged[Url] urlParams: Map[String,String],
  *     @multi @tagged[Body] bodyParams: Map[String,Json]
  *   ): Future[Json]
  * }
  * }}}
  *
  * NOTE: The example above assumes there is a `Json` type defined with appropriate encodings -
  * see [[encoded]] for more details on parameter and method result encoding.
  *
  * The example above configures parameter tag type for the entire trait, but you can also do it for
  * each raw method, e.g.
  *
  * {{{
  * trait RestRawRpc {
  *   @paramTag[RestParam,Body]
  *   def get(...)
  * }
  * }}}
  *
  * @tparam BaseTag    base type for tags that can be used on real RPC parameters
  * @tparam DefaultTag the default tag type used for real parameters not explicitly tagged - if you don't want to
  *                    introduce any specific default tag, just use the same type as for `BaseTag`
  */
final class paramTag[BaseTag <: RpcTag, DefaultTag <: BaseTag] extends RawMethodAnnotation

/**
  * Annotation applied on raw methods or raw parameters that limits matching real methods or real parameters to
  * only these annotated as `Tag`. See [[methodTag]] and [[paramTag]] for more explanation. NOTE: `Tag` may
  * also be some common supertype of multiple tags which are accepted by this raw method or param.
  *
  * @tparam Tag annotation type required to be present on real method or parameter
  */
final class tagged[Tag <: RpcTag] extends RawMethodAnnotation with RawParamAnnotation

/**
  * Raw parameters annotated as `@auxiliary` match real parameters without "consuming" them. This means that
  * real parameters matched by an auxiliary raw parameter must also be matched by some non-auxiliary raw parameter.
  * This way these real params will be matched to more than one raw parameter and effectively duplicated.
  * However, auxiliary raw param may use different encoding that the non-auxiliary one. This may be useful for
  * implementors of raw RPC traits.
  *
  * When extracting real parameter values from raw calls, auxiliary parameters are
  * completely ignored and only the matching non-auxiliary raw param value is used.
  */
final class auxiliary extends RawParamAnnotation

/**
  * Base trait for annotations applied on RPC metadata parameters which tell the macro engine how to materialize
  * their values based on real RPC trait, its methods or their parameters.
  */
sealed trait MetadataParamStrategy extends StaticAnnotation

/**
  * When a metadata parameter is annotated as `@infer`, RPC macro engine will materialize that parameter by searching
  * for an implicit value of that parameter's type. `@infer` is the default strategy assumed for implicit parameters
  * of metadata classes, so using this annotation explicitly is only needed when you want an implicit search done
  * for non-implicit parameter. This may be useful if, e.g. you want an inferred parameter to be a case class field.
  *
  * NOTE: By default, implicit search for `@infer` parameter does NOT affect the decision about whether some real
  * method or real parameter matches a metadata parameter. For example, if an implicit for `@infer` parameter cannot be
  * found, you will only know about it *after* the metadata materializing macro has already been expanded.
  * This behaviour can be changed with [[checked]] annotation.
  */
final class infer extends MetadataParamStrategy

/**
  * Metadata parameter annotated as `@reifyAnnot` is intended to hold annotation(s) that must or may be present on the real
  * RPC trait, method or parameter. `@reifyAnnot` parameters may have arity, which means that they may be annotated as
  * [[single]] (the default), [[optional]] or [[multi]]. Arity annotation determines what parameter type the macro
  * engine expects:
  *
  * - for [[single]], metadata parameter type must extend `StaticAnnotation` and an annotation of that type must be
  * present on the real symbol or compilation error will be raised
  * - for [[optional]], metadata parameter type must be an `Option`/`Opt`/etc. that wraps some `StaticAnnotation`.
  * If that annotation is present on the real symbol, it will be reified as metadata value.
  * - for [[multi]], metadata parameter type must be a subtype of `Iterable[StaticAnnotation]`, e.g. `List[SomeAnnot]`.
  * The macro will then reify all annotations of that particular type present on the real symbol as metadata value.
  *
  * NOTE: all annotations are inherited from super/overridden symbols, i.e.
  * - for RPC traits, annotations are inherited from all superclasses and supertraits
  * - for RPC methods, annotations are inherited from all overridden or implemented abstract methods
  * - for RPC parameters, annotations are inherited from all corresponding parameters (by index, not name) from
  * all methods overridden or implemented by method containing the parameter for which metadata is being reified.
  */
final class reifyAnnot extends MetadataParamStrategy

/**
  * Metadata parameter typed as `Boolean` can be annotated with `@hasAnnot[SomeAnnotation]`. Boolean value will then
  * hold information about whether RPC trait, method or parameter for which metadata is materialized is annotated with
  * `SomeAnnotaton` (or any subtype) or not.
  */
final class hasAnnot[T <: StaticAnnotation] extends MetadataParamStrategy

/**
  * This annotation may only be applied on metadata parameters of type `String` and instructs the macro engine
  * to reify the name of real RPC trait/method/parameter. Depending on the value of `rpcName` flag, the macro
  * will either take into account or ignore potential [[rpcName]] annotation.
  */
final class reifyName(val rpcName: Boolean = false) extends MetadataParamStrategy

/**
  * Metadata parameter annotated with this annotation must be of type `ParamPosition` - a class that holds
  * parameter index information - see scaladoc for `ParamPosition` for more details.
  */
final class reifyPosition extends MetadataParamStrategy

/**
  * Metadata parameter annotated with this annotation must be of type `ParamFlags` - a class that holds
  * parameter flags information - see scaladoc for `ParamFlags` for more details.
  */
final class reifyFlags extends MetadataParamStrategy

/**
  * May be applied on metadata parameters with [[infer]] annotation (or just implicit metadata parameters -
  * they have [[infer]] strategy by default). Metadata parameter annotated as [[checked]] makes the implicit search
  * for that metadata parameter influence the decision about whether some metadata parameter matches real method or
  * param or not. Without [[checked]] annotation, when implicit search for metadata parameter fails, the macro engine
  * ignores that fact and compilation error is deferred until macro is fully expanded.
  */
final class checked extends StaticAnnotation
