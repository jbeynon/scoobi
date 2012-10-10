package com.nicta.scoobi
package impl
package exec

import plan.comp._
import application.ScoobiConfiguration
import plan.graph.{OutputChannel, InputChannel}
import io.{DataSink, DataSource}

/**
 * GADT representing elementary computations to perform in hadoop jobs
 */
sealed trait ExecutionNode extends CompNode {
  def referencedNode: CompNode
}

case class Ref[T <: CompNode](n: T)
case class LoadExec[T](load: Ref[Load[T]]) extends ExecutionNode {
  def referencedNode = load.n
}
case class GroupByKeyExec[K, V](gbk: Ref[GroupByKey[K, V]], n: CompNode) extends ExecutionNode {
  def referencedNode = gbk.n
}
case class CombineExec[K, V](cb: Ref[Combine[K, V]], n: CompNode) extends ExecutionNode {
  def referencedNode = cb.n
}
case class FlattenExec[T](flatten: Ref[Flatten[T]], ins: Seq[CompNode]) extends ExecutionNode {
  def referencedNode = flatten.n
}

case class ReturnExec[T](rt: Ref[Return[T]]) extends ExecutionNode {
  def referencedNode = rt.n
  def env(implicit sc: ScoobiConfiguration) = Env(rt.n.wf, sc)
}
case class MaterializeExec[T](mat: Ref[Materialize[T]], n: CompNode) extends ExecutionNode {
  def referencedNode = mat.n
  def env(implicit sc: ScoobiConfiguration) = Env(mat.n.wf, sc)
}
case class OpExec[A, B, C](op: Ref[Op[A, B, C]], a: CompNode, b: CompNode) extends ExecutionNode {
  def referencedNode = op.n
  def env(implicit sc: ScoobiConfiguration) = Env(op.n.wf, sc)
}

/** specialised ParallelDo to be translated to a Hadoop Mapper class */
case class MapperExec[A, B, E](pd: Ref[ParallelDo[A, B, E]], n: CompNode) extends ExecutionNode {
  def referencedNode = pd.n
}
/** specialised ParallelDo to be translated to a Hadoop Reducer class */
case class ReducerExec[A, B, E](pd: Ref[ParallelDo[A, B, E]], n: CompNode) extends ExecutionNode {
  def referencedNode = pd.n
}
/** specialised ParallelDo to be translated to a Hadoop Reducer class, following a Gbk */
case class GbkReducerExec[A, B, E](pd: Ref[ParallelDo[A, B, E]], n: CompNode) extends ExecutionNode {
  def referencedNode = pd.n
}

case class MscrExec(inputs: Set[InputChannel] = Set(), outputs: Set[OutputChannel] = Set()) {
  def inputChannels:  Set[InputChannelExec]  = inputs. map(_.asInstanceOf[InputChannelExec])
  def outputChannels: Set[OutputChannelExec] = outputs.map(_.asInstanceOf[OutputChannelExec])
}

sealed trait InputChannelExec extends InputChannel {
  def source: DataSource[_,_,_] = input.referencedNode.dataSource
  def input: ExecutionNode
  def tags: Set[Int]
}

/**
 * @param nodes: list of related MapperExec nodes
 */
case class MapperInputChannelExec(nodes: Seq[CompNode], tags: Set[Int] = Set()) extends InputChannelExec {
  def input: ExecutionNode = nodes.head.asInstanceOf[ExecutionNode]
}

/**
 * @param in
 */
case class BypassInputChannelExec(in: CompNode, tags: Set[Int] = Set()) extends InputChannelExec {
  def input: ExecutionNode = in.asInstanceOf[ExecutionNode]
}
/**
 * @param in
 */
case class StraightInputChannelExec(in: CompNode, tags: Set[Int] = Set()) extends InputChannelExec {
  def input: ExecutionNode = in.asInstanceOf[ExecutionNode]
}

sealed trait OutputChannelExec extends OutputChannel {
  def sinks: Seq[DataSink[_,_,_]]
  def tag: Int
}

case class GbkOutputChannelExec(groupByKey: CompNode,
                                flatten:    Option[CompNode]     = None,
                                combiner:   Option[CompNode]     = None,
                                reducer:    Option[CompNode]     = None,
                                sinks:      Seq[DataSink[_,_,_]] = Seq(),
                                tag:        Int = 0) extends OutputChannelExec {

  override def equals(a: Any) = a match {
    case o: GbkOutputChannelExec => o.groupByKey == groupByKey
    case _                       => false
  }

}

case class FlattenOutputChannelExec(in: CompNode, sinks: Seq[DataSink[_,_,_]] = Seq(), tag: Int = 0) extends OutputChannelExec {
  override def equals(a: Any) = a match {
    case o: FlattenOutputChannelExec => o.in == in
    case _                           => false
  }
}
case class BypassOutputChannelExec(in: CompNode, sinks: Seq[DataSink[_,_,_]] = Seq(), tag: Int = 0) extends OutputChannelExec {
  override def equals(a: Any) = a match {
    case o: BypassOutputChannelExec => o.in == in
    case _                          => false
  }
}
