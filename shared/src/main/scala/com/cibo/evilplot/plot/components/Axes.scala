/*
 * Copyright (c) 2018, CiBO Technologies, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.cibo.evilplot.plot.components

import com.cibo.evilplot.geometry._
import com.cibo.evilplot.numeric._
import com.cibo.evilplot.plot.Plot
import com.cibo.evilplot.plot.aesthetics.Theme
import com.cibo.evilplot.plot.renderers.{GridLineRenderer, TickRenderer}

object Axes {

  private sealed trait AxisPlotComponent extends PlotComponent {
    final override val repeated: Boolean = true

    val discrete: Boolean
    val tickRenderer: TickRenderer

    def getDescriptor(plot: Plot, fixed: Boolean): AxisDescriptor

    final protected def ticks(descriptor: AxisDescriptor): Seq[Drawable] =
      descriptor.labels.map(tickRenderer.render)
  }

  private sealed trait ContinuousAxis {
    final val discrete: Boolean = false
    val tickCount: Int
    val tickCountRange: Option[Seq[Int]]
    val labelFormatter: Option[Double => String] = None
    def bounds(plot: Plot): Bounds
    def getDescriptor(plot: Plot, fixed: Boolean): AxisDescriptor =
      Labeling.label(
        bounds(plot),
        preferredTickCount = Some(tickCount),
        tickCountRange = tickCountRange,
        formatter = labelFormatter,
        fixed = fixed)
  }

  private sealed trait DiscreteAxis {
    final val discrete: Boolean = true
    val labels: Seq[(String, Double)]
    def getDescriptor(plot: Plot, fixed: Boolean): AxisDescriptor = DiscreteAxisDescriptor(labels)
  }

  private sealed trait XAxisPlotComponent extends AxisPlotComponent {
    final val position: Position = Position.Bottom
    override def size(plot: Plot): Extent =
      ticks(getDescriptor(plot, fixed = true)).maxBy(_.extent.height).extent

    def bounds(plot: Plot): Bounds = plot.xbounds

    def render(plot: Plot, extent: Extent)(implicit theme: Theme): Drawable = {
      val descriptor = getDescriptor(plot, fixed = true)
      val scale = extent.width / descriptor.axisBounds.range
      // Move the tick to the center of the range for discrete axes.
      val offset = (if (discrete) scale / 2 else 0) - descriptor.axisBounds.min * scale
      ticks(descriptor)
        .zip(descriptor.values)
        .map {
          case (tick, value) =>
            val x = offset + value * scale - tick.extent.width / 2
            if (x <= extent.width) {
              tick.translate(x = x)
            } else EmptyDrawable()
        }
        .group
    }
  }

  private sealed trait YAxisPlotComponent extends AxisPlotComponent {
    final val position: Position = Position.Left
    override def size(plot: Plot): Extent =
      ticks(getDescriptor(plot, fixed = true)).maxBy(_.extent.width).extent

    def bounds(plot: Plot): Bounds = plot.ybounds

    def render(plot: Plot, extent: Extent)(implicit theme: Theme): Drawable = {
      val descriptor = getDescriptor(plot, fixed = true)
      val scale = extent.height / descriptor.axisBounds.range
      val ts = ticks(descriptor)
      val maxWidth = ts.maxBy(_.extent.width).extent.width
      // Move the tick to the center of the range for discrete axes.
      val offset = (if (discrete) scale / 2 else 0) - scale * descriptor.axisBounds.min
      val drawable = ts
        .zip(descriptor.values)
        .map {
          case (tick, value) =>
            val y = extent.height - (value * scale + offset) - tick.extent.height / 2.0
            if (y <= extent.height) {
              tick.translate(x = maxWidth - tick.extent.width, y = y)
            } else EmptyDrawable()
        }
        .group
      drawable.translate(x = extent.width - drawable.extent.width)
    }
  }

  private sealed trait ArbitraryAxisPlotComponent extends AxisPlotComponent {
    override def size(plot: Plot): Extent = {
      val extents = ticks(getDescriptor(plot, fixed = true)).map(_.extent)
      position match {
        case Position.Left | Position.Right => extents.maxBy(_.width)
        case Position.Bottom | Position.Top => extents.maxBy(_.height)
        case _ => Extent(extents.maxBy(_.width).width, extents.maxBy(_.height).height) //XXX does size matter for these cases?
      }
    }

    def bounds(plot: Plot): Bounds //XXX note to self: used in Labeling

    def render(plot: Plot, extent: Extent)(implicit theme: Theme): Drawable = {
      val descriptor = getDescriptor(plot, fixed = true)
      //XXX TODO replace with scaling
      val scale = position match {
        case Position.Left | Position.Right => extent.height / descriptor.axisBounds.range
        case Position.Bottom | Position.Top => extent.width / descriptor.axisBounds.range
        case _ => 1 //XXX
      }
      val ts = ticks(descriptor)
      val maxWidth = ts.maxBy(_.extent.width).extent.width
      // Move the tick to the center of the range for discrete axes.
      val offset = (if (discrete) scale / 2 else 0) - scale * descriptor.axisBounds.min //XXX band scaling
      position match {
        case Position.Left | Position.Right =>
          val drawable = ts
            .zip(descriptor.values)
            .map {
              case (tick, value) =>
                val y = extent.height - (value * scale + offset) - tick.extent.height / 2.0
                if (y <= extent.height) {
                  position match {
                    case Position.Left  => tick.translate(x = maxWidth - tick.extent.width, y = y)
                    case _ => tick.translate(y = y)
                  }
                } else EmptyDrawable()
            }
            .group
          drawable.translate(x = extent.width - drawable.extent.width)
        case Position.Bottom | Position.Top =>
          ts
            .zip(descriptor.values)
            .map {
              case (tick, value) =>
                val x = offset + value * scale - tick.extent.width / 2
                if (x <= extent.width) {
                  tick.translate(x = x)
                } else EmptyDrawable()
            }
            .group
        case _ => ts.group
      }
    }
  }

  private case class HackedXAxisPlotComponent(
    bounds: Bounds,
    override val position: Position,
    tickCount: Int,
    tickRenderer: TickRenderer,
    override val labelFormatter: Option[Double => String],
    tickCountRange: Option[Seq[Int]]
  ) extends ArbitraryAxisPlotComponent
    with ContinuousAxis {
    override def bounds(plot: Plot): Bounds = bounds
  }

  private case class HackedYAxisPlotComponent(
    bounds: Bounds,
    override val position: Position,
    tickCount: Int,
    tickRenderer: TickRenderer,
    override val labelFormatter: Option[Double => String],
    tickCountRange: Option[Seq[Int]]
  ) extends ArbitraryAxisPlotComponent
    with ContinuousAxis {
    override def bounds(plot: Plot): Bounds = bounds
  }

  private case class ContinuousXAxisPlotComponent(
    tickCount: Int,
    tickRenderer: TickRenderer,
    override val labelFormatter: Option[Double => String],
    tickCountRange: Option[Seq[Int]]
  ) extends XAxisPlotComponent
      with ContinuousAxis

  private case class DiscreteXAxisPlotComponent(
    labels: Seq[(String, Double)],
    tickRenderer: TickRenderer
  ) extends XAxisPlotComponent
      with DiscreteAxis

  private case class ContinuousYAxisPlotComponent(
    tickCount: Int,
    tickRenderer: TickRenderer,
    override val labelFormatter: Option[Double => String],
    tickCountRange: Option[Seq[Int]]
  ) extends YAxisPlotComponent
      with ContinuousAxis

  private case class DiscreteYAxisPlotComponent(
    labels: Seq[(String, Double)],
    tickRenderer: TickRenderer
  ) extends YAxisPlotComponent
      with DiscreteAxis

  private sealed trait GridComponent extends PlotComponent {
    val lineRenderer: GridLineRenderer
    def getDescriptor(plot: Plot, fixed: Boolean): AxisDescriptor

    final val position: Position = Position.Background
    override final val repeated: Boolean = true

    protected def lines(descriptor: AxisDescriptor, extent: Extent): Seq[Drawable] =
      descriptor.labels.map(l => lineRenderer.render(extent, l))
  }

  private trait XGridComponent extends GridComponent {
    def bounds(plot: Plot): Bounds = plot.xbounds
    def render(plot: Plot, extent: Extent)(implicit theme: Theme): Drawable = {
      val descriptor = getDescriptor(plot, fixed = true)
      val scale = extent.width / descriptor.axisBounds.range
      lines(descriptor, extent)
        .zip(descriptor.values)
        .map {
          case (line, value) =>
            line.translate(
              x = (value - descriptor.axisBounds.min) * scale - line.extent.width / 2.0)
        }
        .group
    }
  }

  private trait YGridComponent extends GridComponent {
    def bounds(plot: Plot): Bounds = plot.ybounds
    def render(plot: Plot, extent: Extent)(implicit theme: Theme): Drawable = {
      val descriptor = getDescriptor(plot, fixed = true)
      val scale = extent.height / descriptor.axisBounds.range
      val ls = lines(descriptor, extent)
      val maxWidth = ls.maxBy(_.extent.width).extent.width
      ls.zip(descriptor.values)
        .map {
          case (line, value) =>
            val y = (value - descriptor.axisBounds.min) * scale + line.extent.height / 2.0
            line.translate(x = maxWidth - line.extent.width, y = extent.height - y)
        }
        .group
    }
  }

  private case class ContinuousXGridComponent(
    tickCount: Int,
    lineRenderer: GridLineRenderer,
    tickCountRange: Option[Seq[Int]]
  ) extends XGridComponent
      with ContinuousAxis

  private case class ContinuousYGridComponent(
    tickCount: Int,
    lineRenderer: GridLineRenderer,
    tickCountRange: Option[Seq[Int]]
  ) extends YGridComponent
      with ContinuousAxis

  trait AxesImplicits {
    protected val plot: Plot

    /** Add an X axis to the plot.
      * @param tickCount    The number of tick lines.
      * @param tickRenderer Function to draw a tick line/label.
      * @param labelFormatter Custom function to format tick labels.
      * @param tickCountRange Allow searching over axis labels with this many ticks.
      */
    def xAxis(
      tickCount: Option[Int] = None,
      tickRenderer: Option[TickRenderer] = None,
      labelFormatter: Option[Double => String] = None,
      tickCountRange: Option[Seq[Int]] = None,
      newBounds: Option[Bounds] = None
    )(implicit theme: Theme): Plot = {
      val component = ContinuousXAxisPlotComponent(
        tickCount.getOrElse(theme.elements.xTickCount),
        tickRenderer.getOrElse(
          TickRenderer.xAxisTickRenderer(
            length = theme.elements.tickLength,
            thickness = theme.elements.tickThickness,
            rotateText = theme.elements.continuousXAxisLabelOrientation
          )),
        labelFormatter,
        tickCountRange
      )
      component +: plot.xbounds(component.getDescriptor(plot, plot.xfixed).axisBounds)
    }

    //XXX What would be convenient for tying axes to data?
    // manual bounds?
    // a plot?
    // sequence of data/points?
    // ------
    // manual or plot seem like the easiest jumps from where we are now...
    // ... but revisit once axes get severed from plots/bounds
    //XXX call to scaling version from bounds version (assume linear)
    def xHackedAxis(
      bounds: Bounds,
      position: Position,
      tickCount: Option[Int] = None,
      tickRenderer: Option[TickRenderer] = None,
      labelFormatter: Option[Double => String] = None,
      tickCountRange: Option[Seq[Int]] = None
    )(implicit theme: Theme): Plot = {
      val component = HackedXAxisPlotComponent(
        bounds,
        position,
        tickCount.getOrElse(theme.elements.xTickCount),
        tickRenderer.getOrElse(
          TickRenderer.ArbitraryAxisTickRenderer(
            position,
            length = theme.elements.tickLength,
            thickness = theme.elements.tickThickness
          )),
        labelFormatter,
        tickCountRange
      )
      component +: plot //XXX make prepending component optional?
    }

    /** Add an X axis to the plot
      * @param labels The labels. The x values are assumed to start at 0 and increment by one for each label.
      */
    def xAxis(labels: Seq[String])(implicit theme: Theme): Plot =
      xAxis(labels, labels.indices.map(_.toDouble))

    /** Add an X axis to the plot.
      * @param labels The labels.
      * @param values The X value for each label.
      */
    def xAxis(labels: Seq[String], values: Seq[Double])(implicit theme: Theme): Plot = {
      require(labels.lengthCompare(values.length) == 0)
      val labelsAndValues = labels.zip(values)
      val component = DiscreteXAxisPlotComponent(
        labelsAndValues,
        TickRenderer.xAxisTickRenderer(
          length = theme.elements.tickLength,
          thickness = theme.elements.tickThickness,
          rotateText = theme.elements.categoricalXAxisLabelOrientation)
      )
      component +: plot.xbounds(component.getDescriptor(plot, plot.xfixed).axisBounds)
    }

    /** Add a Y axis to the plot.
      * @param tickCount    The number of tick lines.
      * @param tickRenderer Function to draw a tick line/label.
      * @param labelFormatter Custom function to format tick labels.
      * @param tickCountRange Allow searching over axis labels with this many ticks.
      */
    def yAxis(
      tickCount: Option[Int] = None,
      tickRenderer: Option[TickRenderer] = None,
      labelFormatter: Option[Double => String] = None,
      tickCountRange: Option[Seq[Int]] = None
    )(implicit theme: Theme): Plot = {
      val component = ContinuousYAxisPlotComponent(
        tickCount.getOrElse(theme.elements.yTickCount),
        tickRenderer.getOrElse(
          TickRenderer.yAxisTickRenderer(
            length = theme.elements.tickLength,
            thickness = theme.elements.tickThickness
          )),
        labelFormatter,
        tickCountRange
      )
      component +: plot.ybounds(component.getDescriptor(plot, plot.yfixed).axisBounds)
    }

    def yHackedAxis(
      bounds: Bounds,
      position: Position,
      tickCount: Option[Int] = None,
      tickRenderer: Option[TickRenderer] = None,
      labelFormatter: Option[Double => String] = None,
      tickCountRange: Option[Seq[Int]] = None
    )(implicit theme: Theme): Plot = {
      val component = HackedYAxisPlotComponent(
        bounds,
        position,
        tickCount.getOrElse(theme.elements.xTickCount),
        tickRenderer.getOrElse(
          TickRenderer.ArbitraryAxisTickRenderer(
            position,
            length = theme.elements.tickLength,
            thickness = theme.elements.tickThickness
          )),
        labelFormatter,
        tickCountRange
      )
      component +: plot
    }

    /** Add a Y axis to the plot.
      * @param labels The label. The y values are assumed to start at 0 and increment by one for each label.
      */
    def yAxis(labels: Seq[String])(implicit theme: Theme): Plot =
      yAxis(labels, labels.indices.map(_.toDouble))

    /** Add a Y axis to the plot.
      * @param labels The labels.
      * @param values The Y value for each label.
      */
    def yAxis(labels: Seq[String], values: Seq[Double])(implicit theme: Theme): Plot = {
      require(labels.lengthCompare(values.length) == 0)
      val labelsAndValues = labels.zip(values)
      val component = DiscreteYAxisPlotComponent(
        labelsAndValues,
        TickRenderer.yAxisTickRenderer(
          length = theme.elements.tickLength,
          thickness = theme.elements.tickThickness
        ))
      component +: plot.ybounds(component.getDescriptor(plot, plot.yfixed).axisBounds)
    }

    /** Add x grid lines to the plot.
      * @param lineCount the number of grid lines to use
      * @param lineRenderer the grid line renderer
      */
    def xGrid(
      lineCount: Option[Int] = None,
      lineRenderer: Option[GridLineRenderer] = None,
      tickCountRange: Option[Seq[Int]] = None
    )(implicit theme: Theme): Plot = {
      val component = ContinuousXGridComponent(
        lineCount.getOrElse(theme.elements.xGridLineCount),
        lineRenderer.getOrElse(GridLineRenderer.xGridLineRenderer()),
        tickCountRange
      )
      plot.xbounds(component.getDescriptor(plot, plot.xfixed).axisBounds) :+ component
    }

    /** Add y grid lines to the plot.
      * @param lineCount the number of grid lines to use
      * @param lineRenderer the grid line renderer
      */
    def yGrid(
      lineCount: Option[Int] = None,
      lineRenderer: Option[GridLineRenderer] = None,
      tickCountRange: Option[Seq[Int]] = None
    )(implicit theme: Theme): Plot = {
      val component = ContinuousYGridComponent(
        lineCount.getOrElse(theme.elements.yGridLineCount),
        lineRenderer.getOrElse(GridLineRenderer.yGridLineRenderer()),
        tickCountRange
      )
      plot.ybounds(component.getDescriptor(plot, plot.yfixed).axisBounds) :+ component
    }
  }
}
