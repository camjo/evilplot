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

package com.cibo.evilplot.plot.renderers

import com.cibo.evilplot.geometry._
import com.cibo.evilplot.plot.aesthetics.Theme
import com.cibo.evilplot.plot.components.Position

trait TickRenderer {
  def render(label: String): Drawable
}

object TickRenderer {

  val defaultTickThickness: Double = 1
  val defaultTickLength: Double = 5

  /** Create a renderer to render a tick on the x axis.
    *
    * @param length     The length of the tick line.
    * @param thickness  The thickness of the tick line.
    * @param rotateText The rotation of the label.
    */
  def xAxisTickRenderer(
    length: Double = defaultTickLength,
    thickness: Double = defaultTickThickness,
    rotateText: Double = 0
  )(implicit theme: Theme): TickRenderer = new TickRenderer {
    private def align(tick: Drawable, label: Drawable): Drawable =
      if (rotateText.toInt % 90 == 0) Align.center(tick, label).reduce(_ above _)
      else tick.above(label).transX(label.extent.width)

    def render(label: String): Drawable = {
      val line = Line(length, thickness).rotated(90)
      align(
        line.colored(theme.colors.tickLabel),
        Style(
          Text(label.toString, size = theme.fonts.tickLabelSize, fontFace = theme.fonts.fontFace),
          theme.colors.tickLabel)
          .rotated(rotateText)
          .padTop(2)
      )
    }
  }

  /** Create a renderer to render a tick on the y axis.
    *
    * @param length    The length of the tick line.
    * @param thickness The thickness of the tick line.
    */
  def yAxisTickRenderer(
    length: Double = defaultTickLength,
    thickness: Double = defaultTickThickness
  )(implicit theme: Theme): TickRenderer = new TickRenderer {
    def render(label: String): Drawable = {
      val line = Line(length, thickness)
      Align
        .middle(
          Style(
            Text(label.toString, size = theme.fonts.tickLabelSize, fontFace = theme.fonts.fontFace),
            theme.colors.tickLabel)
            .padRight(2)
            .padBottom(2),
          line.colored(theme.colors.tickLabel)
        )
        .reduce(beside)
    }
  }

  def ArbitraryAxisTickRenderer(
    position: Position,
    length: Double = defaultTickLength,
    thickness: Double = defaultTickThickness
  )(implicit theme: Theme): TickRenderer = new TickRenderer {
    def render(label: String): Drawable = {
      val line = Line(length, thickness).colored(theme.colors.tickLabel)
      val verticalLine = line.rotated(90)
      val text = Style(
        Text(label.toString, size = theme.fonts.tickLabelSize, fontFace = theme.fonts.fontFace),
        theme.colors.tickLabel
      )
      //XXX TODO rotated text support
      position match {
        case Position.Left =>
          Align.middle(text.padRight(2).padBottom(2), line).reduce(beside)
        case Position.Right =>
          Align.middle(line, text.padLeft(2).padBottom(2)).reduce(beside)
        case Position.Bottom =>
          Align.center(verticalLine, text.padTop(2)).reduce(_ above _)
        case Position.Top =>
          Align.center(text.padBottom(2), verticalLine).reduce(_ above _)
        case _ =>
          text
      }
    }
  }
}
