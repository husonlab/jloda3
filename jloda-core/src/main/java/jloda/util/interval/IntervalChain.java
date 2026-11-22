/*
 * IntervalChain.java Copyright (C) 2026 Daniel H. Huson
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

package jloda.util.interval;

/**
 * chain of disjoint intervals
 * Daniel Huson, 3.2018
 */
public class IntervalChain {
    private Interval head;

    public void clear() {
        head = null;
    }

    /**
     * add an interval to the chain
     *
     * @param a start, 1-based, inclusive
     * @param b end, 1-based, inclusive
     */
    public void add(int a, int b) {
        if (a > b) { // swap if wrong way around
            int tmp = a;
            a = b;
            b = tmp;
        }

        // everything that ends before a stays as it is
        Interval prev = null;
        Interval current = head;
        while (current != null && current.getB() < a) {
            prev = current;
            current = current.getNext();
        }
        // everything that overlaps [a,b] is taken into it. There may be several: handling only one on each
        // side and leaving those in between in the chain is what made getLength() count a base twice
        while (current != null && current.getA() <= b) {
            a = Math.min(a, current.getA());
            b = Math.max(b, current.getB());
            current = current.getNext();
        }
        final Interval merged = new Interval(a, b, current);
        if (prev == null)
            head = merged;
        else
            prev.setNext(merged);
    }

    /**
     * report the length of the intervals
     *
     * @return length
     */
    public int getLength() {
        int count = 0;
        Interval interval = head;
        while (interval != null) {
            count += (interval.getB() - interval.getA() + 1);
            interval = interval.getNext();
        }
        return count;
    }

    public String toString() {
        final var buf = new StringBuilder();
        buf.append("Intervals: ");

        var interval = head;
        while (interval != null) {
            buf.append(String.format("[%d,%d] ", interval.getA(), interval.getB()));
            interval = interval.getNext();
        }
        buf.append(" length: ").append(getLength());
        return buf.toString();
    }

    /**
     * single-linked interval node
     */
    static class Interval {
        private int a;
        private int b;
        private Interval next;

        public Interval(int a, int b, Interval next) {
            this.a = a;
            this.b = b;
            this.next = next;
        }


        public int getA() {
            return a;
        }

        public void setA(int a) {
            this.a = a;
        }

        public int getB() {
            return b;
        }

        public void setB(int b) {
            this.b = b;
        }

        public Interval getNext() {
            return next;
        }

        public void setNext(Interval next) {
            this.next = next;
        }
    }

    public static void main(String[] args) {

        int[] pairs = {11, 20, 71, 80, 31, 41, 50, 60, 36, 55, 21, 25, 68, 72, 15, 18};

        final var intervalChain = new IntervalChain();

        for (var i = 0; i < pairs.length; i += 2) {
            System.err.println("Adding: " + pairs[i] + "," + pairs[i + 1]);
            intervalChain.add(pairs[i], pairs[i + 1]);
            System.err.println(intervalChain);
        }
    }

}
