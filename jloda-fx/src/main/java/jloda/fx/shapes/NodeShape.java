/*
 * NodeShape.java Copyright (C) 2024 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package jloda.fx.shapes;

import javafx.scene.Node;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.Shape3D;
import javafx.scene.shape.Sphere;
import jloda.util.StringUtils;

/**
 * some shapes
 * Daniel Huson, 2020
 */
public enum NodeShape {
    Square, Circle, TriangleUp, TriangleDown, Diamond, Hexagon, Rectangle, Oval, None;

    /**
     * determines the node shape of a shape
     *
     * @return node shape
     */
    public static NodeShape valueOf(Shape shape) {
        if (/*shape instanceof CircleShape || */shape instanceof javafx.scene.shape.Circle)
            return Circle;
        else if (shape instanceof SquareShape)
            return Square;
        else if (shape instanceof DiamondShape)
            return Diamond;
        else if (shape instanceof HexagonShape)
            return Hexagon;
        else if (shape instanceof TriangleUpShape)
            return TriangleUp;
        else if (shape instanceof TriangleDownShape)
            return TriangleDown;
        else if (shape instanceof RectangleShape || shape instanceof javafx.scene.shape.Rectangle)
            return Rectangle;
        else if (shape instanceof OvalShape)
            return Oval;
        else
            return None;
    }

    /**
     * gets the short code for this shape, which consists only of the capital letters appearing in the name
     */
    public static String getCode(Shape shape) {
        return valueOf(shape).toString().replaceAll("[a-z]", "");
    }

    /**
     * creates a shape
     *
     * @return shape
     */
    public static Shape create(String name, double size) {
        return create(name, size, size);
    }

    /**
     * creates a shape
     *
     * @return shape
     */
    public static Shape create(NodeShape nodeShape, double size) {
        return create(nodeShape, size, size);
    }

    /**
     * creates a shape
     */
    public static Shape create(String name, double width, double height) {
        var nodeShape = StringUtils.valueOfIgnoreCase(NodeShape.class, name);
        if (nodeShape != null)
            return create(nodeShape, width, height);
        else
            return null;
    }

    /**
     * create a shape for a node shape
     *
     * @return shape
     */
    public static Shape create(NodeShape nodeShape, double width, double height) {
        return switch (nodeShape) {
            case Square -> new SquareShape(width);
            case Rectangle -> new RectangleShape(width, height);
            /* case Circle */
            default -> new CircleShape(width);
            case Oval -> new OvalShape(width, height);
            case TriangleUp -> new TriangleUpShape(width, height);
            case TriangleDown -> new TriangleDownShape(width, height);
            case Diamond -> new DiamondShape(width, height);
            case Hexagon -> new HexagonShape(width, height);
        };
    }

    // todo: implement different shapes here
    public static Shape3D create3D(Node shape, double width) {
        ((Sphere) shape).setRadius(0.5 * width);
        return (Sphere) shape;
    }

    public static int[] getSize(Shape shape) {
        return new int[]{(int) Math.round(shape.getBoundsInLocal().getWidth()), (int) Math.round(shape.getBoundsInLocal().getHeight())};
    }

    public static double getWidth(Shape shape) {
        return shape.getBoundsInLocal().getWidth();
    }

    public static double getHeight(Shape shape) {
        return shape.getBoundsInLocal().getHeight();
    }

    /**
     * set the size of a shape (either a NodeShape or a circle or rectangle)
     *
     * @param shape  the shape
     * @param width  desired width (and 2*radius for a circle)
     * @param height desired height (ignored for a circle)
     */
    public static void setSize(Shape shape, double width, double height) {
        if (shape instanceof javafx.scene.shape.Circle circle) {
            circle.setRadius(width / 2);
        } else if (shape instanceof Rectangle rectangle) {
            rectangle.setWidth(width);
            rectangle.setHeight(height);
        } else if (shape instanceof ISized iSized) {
            iSized.setSize(width, height);
        }
    }
}
