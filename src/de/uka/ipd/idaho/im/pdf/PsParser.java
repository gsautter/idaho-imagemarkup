/*
 * Copyright (c) 2006-, IPD Boehm, Universitaet Karlsruhe (TH) / KIT, by Guido Sautter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Universit�t Karlsruhe (TH) / KIT nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY UNIVERSIT�T KARLSRUHE (TH) / KIT AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.uka.ipd.idaho.im.pdf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Vector;

import org.icepdf.core.pobjects.Name;

import de.uka.ipd.idaho.im.pdf.PdfUtils.PdfByteInputStream;

/**
 * @author sautter
 *
 */
public class PsParser {
	
	private static final boolean DEBUG_EXECUTE_PS = false;
	
	private static final boolean REPORT_UNKNOWN_COMMANDS = false; // TODO always set this to false for builds
	
	static void executePs(byte[] data, Hashtable state, LinkedList stack) throws IOException {
		executePs(data, state, stack, null);
	}
	
	static void executePs(byte[] data, Hashtable state, LinkedList stack, String indent) throws IOException {
		PdfByteInputStream pbis = new PdfByteInputStream(new ByteArrayInputStream(data));
		LinkedList dictStack = new LinkedList();
		for (Object next; (next = cropNext(pbis)) != null;) {
			if (next instanceof PsCommand) {
				PsCommand psc = ((PsCommand) next);
				//	TODO implement remaining commands
				
				//	skip access modifiers
				if ("readonly".equals(psc.command))
					continue;
				if ("executeonly".equals(psc.command))
					continue;
				if ("noaccess".equals(psc.command))
					continue;
				
				//	skip 'FontDirectory'
				if ("FontDirectory".equals(psc.command))
					continue;
				
				//	skip file getters (we already have the data)
				if ("currentfile".equals(psc.command))
					continue;
				if ("closefile".equals(psc.command))
					continue;
				
				//	skip marker of encrypted section
				if ("eexec".equals(psc.command))
					continue;
				
				//	start of cascade ...
				if (false) {}
				
				//	put mark on stack
				else if ("mark".equals(psc.command)) {
					stack.addLast("MARK");
				}
				
				//	pop last stack element
				else if ("pop".equals(psc.command)) {
					if (stack.size() != 0)
						stack.removeLast();
				}
				
				//	duplicate last stack element
				else if ("dup".equals(psc.command)) {
					stack.addLast(stack.getLast());
				}
				
				//	swap last two stack elements
				else if ("exch".equals(psc.command)) {
					Object last1 = stack.removeLast();
					Object last2 = stack.removeLast();
					stack.addLast(last1);
					stack.addLast(last2);
				}
				
				//	duplicate a stack element
				else if ("index".equals(psc.command)) {
					int index = ((Number) stack.removeLast()).intValue();
					stack.addLast(stack.get(stack.size() - 1 - index));
				}
				
				//	roll the n topmost stack elements by j
				else if ("roll".equals(psc.command)) {
					int j = ((Number) stack.removeLast()).intValue();
					int n = ((Number) stack.removeLast()).intValue();
					if (j < 0) {
						for (int i = 0; i < -j; i++)
							stack.addLast(stack.remove(stack.size() - n));
					}
					else if (j > 0)
						for (int i = 0; i < j; i++) {
							stack.add((stack.size() - n), stack.getLast());
							stack.removeLast();
						}
				}
				
				//	copy the topmost n stack elements
				else if ("copy".equals(psc.command)) {
					int n = ((Number) stack.removeLast()).intValue();
					for (int i = 0; i < n; i++)
						stack.addLast(stack.get(stack.size() - n));
				}
				
				
				
				//	create an array
				else if ("array".equals(psc.command)) {
					int length = ((Number) stack.removeLast()).intValue();
					stack.addLast(new Vector(length));
				}
				
				//	create a dictionary
				else if ("dict".equals(psc.command)) {
					int size = ((Number) stack.removeLast()).intValue();
					stack.addLast(new Hashtable(size));
				}
				
				//	duplicate a dictionary onto the stack
				else if ("currentdict".equals(psc.command)) {
					stack.addLast(dictStack.getLast());
				}
				
				//	put an element into an array or dictionary
				else if ("put".equals(psc.command)) {
					Object element = stack.removeLast();
					Object nameOrIndex = stack.removeLast();
					Object dictOrArray = stack.removeLast();
					try {
						if (dictOrArray instanceof Vector) {
							int index = ((Number) nameOrIndex).intValue();
							Vector array = ((Vector) dictOrArray);
							if (index < array.size())
								array.set(index, element);
							array.add(index, element);
//							System.out.println("Set " + index + " to " + element);
//							System.out.println("Stack is: " + stack);
						}
						else if (dictOrArray instanceof Hashtable) {
							Name key = ((Name) nameOrIndex);
							Hashtable dict = ((Hashtable) dictOrArray);
							dict.put(key, element);
//							System.out.println("Set " + key + " to " + element);
//							System.out.println("Stack is: " + stack);
						}
						else {
							stack.addLast(dictOrArray);
							stack.addLast(nameOrIndex);
							stack.addLast(element);
							System.out.println("Strange put stack is: " + stack);
							System.out.println("Strange put dict stack is: " + dictStack);
							System.out.println("Strange put state is: " + state);
						}
					}
					catch (RuntimeException re) {
						stack.addLast(dictOrArray);
						stack.addLast(nameOrIndex);
						stack.addLast(element);
						System.out.println("Error stack is: " + stack);
						System.out.println("Error dict stack is: " + dictStack);
						System.out.println("Error state is: " + state);
						throw re;
					}
				}
				
				//	get an element from an array or dictionary
				else if ("get".equals(psc.command)) {
					Object nameOrIndex = stack.removeLast();
					Object dictOrArray = stack.removeLast();
					try {
						if (dictOrArray instanceof Vector) {
							int index = ((Number) nameOrIndex).intValue();
							Vector array = ((Vector) dictOrArray);
							stack.addLast(array.get(index));
						}
						else if (dictOrArray instanceof Hashtable) {
							Name key = ((Name) nameOrIndex);
							Hashtable dict = ((Hashtable) dictOrArray);
							Object value = dict.get(key);
							stack.addLast(value);
						}
						else {
							stack.addLast(dictOrArray);
							stack.addLast(nameOrIndex);
							System.out.println("Strange get stack is: " + stack);
							System.out.println("Strange get dict stack is: " + dictStack);
							System.out.println("Strange get state is: " + state);
						}
					}
					catch (RuntimeException re) {
						stack.addLast(dictOrArray);
						stack.addLast(nameOrIndex);
						System.out.println("Error stack is: " + stack);
						System.out.println("Error dict stack is: " + dictStack);
						System.out.println("Error state is: " + state);
						throw re;
					}
				}
				
				//	define a variable in state
				else if ("def".equals(psc.command)) {
					Object value = stack.removeLast();
					Name key = ((Name) stack.removeLast());
					Hashtable dict = (dictStack.isEmpty() ? state : ((Hashtable) dictStack.getLast()));
					dict.put(key, value);
//					System.out.println("Set " + key + " to " + value);
//					System.out.println("Stack is: " + stack);
				}
				
				//	known command
				else if ("known".equals(psc.command)) {
					Name key = ((Name) stack.removeLast());
					Hashtable dict = (dictStack.isEmpty() ? state : ((Hashtable) dictStack.getLast()));
					stack.addLast(new Boolean(dict.containsKey(key)));
				}
				
				//	push dictionary on dedicated stack
				else if ("begin".equals(psc.command)) {
					Hashtable dict = ((Hashtable) stack.removeLast());
					dictStack.addLast(dict);
				}
				
				//	pop dictionary off dedicated stack
				else if ("end".equals(psc.command)) {
					Hashtable dict = ((Hashtable) dictStack.removeLast());
//					System.out.println("Got dictionary: " + dict);
				}
				
				
				
				//	if command
				else if ("if".equals(psc.command)) {
					System.out.println("If stack is: " + stack);
					Object tObj = stack.removeLast();
					Object bObj = stack.removeLast();
					while (bObj instanceof PsProcedure) {
						PsProcedure bProc = ((PsProcedure) bObj);
						System.out.println("Executing " + bProc.toString());
						bProc.execute(state, stack);
						bObj = stack.removeLast();
					}
					if (((Boolean) bObj).booleanValue()) {
						if (tObj instanceof PsProcedure) {
							PsProcedure tProc = ((PsProcedure) tObj);
							System.out.println("Executing " + tProc.toString());
							tProc.execute(state, stack);
						}
						else stack.addLast(tObj);
					}
				}
				
				//	if/else command
				else if ("ifelse".equals(psc.command)) {
					System.out.println("IfElse stack is: " + stack);
					Object fObj = stack.removeLast();
					Object tObj = stack.removeLast();
					Object bObj = stack.removeLast();
					while (bObj instanceof PsProcedure) {
						PsProcedure bProc = ((PsProcedure) bObj);
						System.out.println("Executing " + bProc.toString());
						bProc.execute(state, stack);
						bObj = stack.removeLast();
					}
					if (((Boolean) bObj).booleanValue()) {
						if (tObj instanceof PsProcedure) {
							PsProcedure tProc = ((PsProcedure) tObj);
							System.out.println("Executing " + tProc.toString());
							tProc.execute(state, stack);
						}
						else stack.addLast(tObj);
					}
					else {
						if (fObj instanceof PsProcedure) {
							PsProcedure fProc = ((PsProcedure) fObj);
							System.out.println("Executing " + fProc.toString());
							fProc.execute(state, stack);
						}
						else stack.addLast(fObj);
					}
				}
				
				
				
				//	the usual RD, ND, and NP (always named that in Type1 fonts)
				else if ("RD".equals(psc.command)) {
					int length = ((Number) stack.removeLast()).intValue();
					while (pbis.peek() == 32)
						pbis.read();
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					for (int l = 0; l < length; l++)
						baos.write(pbis.read());
					byte[] stopPeek = new byte[3];
					while (true) {
						pbis.peek(stopPeek);
						if (PdfUtils.equals(stopPeek, " ND"))
							break;
						else if (PdfUtils.equals(stopPeek, " NP"))
							break;
						else baos.write(pbis.read());
					}
					stack.addLast(new PsString(baos.toByteArray()));
				}
				else if ("ND".equals(psc.command)) {
					Object value = stack.removeLast();
					Name key = ((Name) stack.removeLast());
					Hashtable dict = (dictStack.isEmpty() ? state : ((Hashtable) dictStack.getLast()));
					dict.put(key, value);
//					System.out.println("Set " + key + " to " + value);
				}
				else if ("NP".equals(psc.command)) {
					Object element = stack.removeLast();
					int index = ((Number) stack.removeLast()).intValue();
					Vector array = ((Vector) stack.removeLast());
					array.add(index, element);
				}
				
				//	define font
				else if ("definefont".equals(psc.command)) {
					Object font = stack.removeLast();
					Object key = stack.removeLast();
					state.put(key, font);
				}
				
				
				
				//	get absolute value of last stack element
				else if ("abs".equals(psc.command))
					stack.addLast(new Float(Math.abs(((Number) stack.removeLast()).floatValue())));
				
				//	negate last stack element
				else if ("neg".equals(psc.command))
					stack.addLast(new Float(-((Number) stack.removeLast()).floatValue()));
				
				//	round last stack element
				else if ("round".equals(psc.command))
					stack.addLast(new Float(Math.round(((Number) stack.removeLast()).floatValue())));
				
				//	round down last stack element
				else if ("floor".equals(psc.command))
					stack.addLast(new Float(Math.floor(((Number) stack.removeLast()).floatValue())));
				
				//	round up last stack element
				else if ("ceiling".equals(psc.command))
					stack.addLast(new Float(Math.ceil(((Number) stack.removeLast()).floatValue())));
				
				//	compute square root of last stack element
				else if ("sqrt".equals(psc.command))
					stack.addLast(new Float(Math.sqrt(((Number) stack.removeLast()).floatValue())));
				
				//	compute natural logarithm of last stack element
				else if ("ln".equals(psc.command))
					stack.addLast(new Float(Math.log(((Number) stack.removeLast()).floatValue())));
				
				//	compute 10 logarithm of last stack element
				else if ("log".equals(psc.command))
					stack.addLast(new Float(Math.log10(((Number) stack.removeLast()).floatValue())));
				
				//	truncate last stack element
				else if ("truncate".equals(psc.command)) {
					float num = ((Number) stack.removeLast()).floatValue();
					stack.addLast(new Float((num < 0) ? Math.ceil(num) : Math.floor(num)));
				}
				
				//	compute sine of last stack element (in degrees)
				else if ("sin".equals(psc.command))
					stack.addLast(new Float(Math.sin((((Number) stack.removeLast()).floatValue() * Math.PI) / 180)));
				
				//	compute cosine of last stack element (in degrees)
				else if ("cos".equals(psc.command))
					stack.addLast(new Float(Math.cos((((Number) stack.removeLast()).floatValue() * Math.PI) / 180)));
				
				//	convert real to integer
				else if ("cvi".equals(psc.command))
					stack.addLast(new Integer(((Number) stack.removeLast()).intValue()));
				
				//	convert integer to real
				else if ("cvr".equals(psc.command))
					stack.addLast(new Float(((Number) stack.removeLast()).floatValue()));
				
				
				//	add last two stack elements
				else if ("add".equals(psc.command)) {
					float num2 = ((Number) stack.removeLast()).floatValue();
					float num1 = ((Number) stack.removeLast()).floatValue();
					stack.addLast(new Float(num1 + num2));
				}
				
				//	subtract last two stack elements from one another
				else if ("sub".equals(psc.command)) {
					float num2 = ((Number) stack.removeLast()).floatValue();
					float num1 = ((Number) stack.removeLast()).floatValue();
					stack.addLast(new Float(num1 - num2));
				}
				
				//	multiply last two stack elements
				else if ("mul".equals(psc.command)) {
					float num2 = ((Number) stack.removeLast()).floatValue();
					float num1 = ((Number) stack.removeLast()).floatValue();
					stack.addLast(new Float(num1 * num2));
				}
				
				//	divide last two stack elements by one another
				else if ("div".equals(psc.command)) {
					float num2 = ((Number) stack.removeLast()).floatValue();
					float num1 = ((Number) stack.removeLast()).floatValue();
					stack.addLast(new Float(num1 / num2));
				}
				
				//	integer divide last two stack elements by one another
				else if ("idiv".equals(psc.command)) {
					int num2 = ((Number) stack.removeLast()).intValue();
					int num1 = ((Number) stack.removeLast()).intValue();
					stack.addLast(new Integer(num1 / num2));
				}
				
				//	take last two stack elements modulo one another
				else if ("mod".equals(psc.command)) {
					int num2 = ((Number) stack.removeLast()).intValue();
					int num1 = ((Number) stack.removeLast()).intValue();
					stack.addLast(new Integer(num1 % num2));
				}
				
				//	power last two stack elements with one another
				else if ("exp".equals(psc.command)) {
					float num2 = ((Number) stack.removeLast()).floatValue();
					float num1 = ((Number) stack.removeLast()).floatValue();
					stack.addLast(new Float(Math.pow(num1, num2)));
				}
				
				//	compute arcus tangens of last two stack elements (in degrees)
				else if ("atan".equals(psc.command)) {
					float num2 = ((Number) stack.removeLast()).floatValue();
					float num1 = ((Number) stack.removeLast()).floatValue();
					stack.addLast(new Float((Math.atan2(num1, num2) * 180) / Math.PI));
				}
				
				
				
				//	compute logical or bit-wise complement of last stack element
				else if ("not".equals(psc.command)) {
					Object obj = stack.removeLast();
					if (obj instanceof Number)
						stack.addLast(new Integer(~((Number) obj).intValue()));
					else if (obj instanceof Boolean)
						stack.addLast(new Boolean(((Boolean) obj).booleanValue() != true));
					else throw new IllegalArgumentException("Cannot negate " + obj);
				}
				
				//	logically or bit-wise 'and' combine last two stack elements
				else if ("and".equals(psc.command)) {
					Object obj2 = stack.removeLast();
					Object obj1 = stack.removeLast();
					if ((obj1 instanceof Number) && (obj2 instanceof Number))
						stack.addLast(new Integer(((Number) obj1).intValue() & ((Number) obj2).intValue()));
					else if ((obj1 instanceof Boolean) && (obj2 instanceof Boolean))
						stack.addLast(new Boolean(((Boolean) obj1).booleanValue() && ((Boolean) obj2).booleanValue()));
					else throw new IllegalArgumentException("Cannot 'and' combine " + obj1 + " and " + obj2);
				}
				
				//	logically or bit-wise 'or' combine last two stack elements
				else if ("or".equals(psc.command)) {
					Object obj2 = stack.removeLast();
					Object obj1 = stack.removeLast();
					if ((obj1 instanceof Number) && (obj2 instanceof Number))
						stack.addLast(new Integer(((Number) obj1).intValue() | ((Number) obj2).intValue()));
					else if ((obj1 instanceof Boolean) && (obj2 instanceof Boolean))
						stack.addLast(new Boolean(((Boolean) obj1).booleanValue() || ((Boolean) obj2).booleanValue()));
					else throw new IllegalArgumentException("Cannot 'or' combine " + obj1 + " and " + obj2);
				}
				
				//	logically or bit-wise 'xor' combine last two stack elements
				else if ("xor".equals(psc.command)) {
					Object obj2 = stack.removeLast();
					Object obj1 = stack.removeLast();
					if ((obj1 instanceof Number) && (obj2 instanceof Number))
						stack.addLast(new Integer(((Number) obj1).intValue() ^ ((Number) obj2).intValue()));
					else if ((obj1 instanceof Boolean) && (obj2 instanceof Boolean))
						stack.addLast(new Boolean(((Boolean) obj1).booleanValue() != ((Boolean) obj2).booleanValue()));
					else throw new IllegalArgumentException("Cannot 'xor' combine " + obj1 + " and " + obj2);
				}
				
				//	bit-shift last two stack elements by one another
				else if ("bitshift".equals(psc.command)) {
					int num2 = ((Number) stack.removeLast()).intValue();
					int num1 = ((Number) stack.removeLast()).intValue();
					if (num2 < 0)
						stack.addLast(new Integer(num1 >>> -num2));
					else stack.addLast(new Integer(num1 << num2));
				}
				
				//	test if last two stack elements are equal
				else if ("eq".equals(psc.command)) {
					Object obj2 = stack.removeLast();
					Object obj1 = stack.removeLast();
					if ((obj1 instanceof Number) && (obj2 instanceof Number))
						stack.addLast(new Boolean(((Number) obj1).floatValue() == ((Number) obj2).floatValue()));
					else if (((obj1 instanceof PsString) || (obj1 instanceof Name)) && ((obj2 instanceof PsString) || (obj2 instanceof Name)))
						stack.addLast(new Boolean(obj1.toString().compareTo(obj2.toString()) == 0));
					else stack.addLast(new Boolean(obj1.equals(obj2)));
				}
				
				//	test if last two stack elements are not equal
				else if ("ne".equals(psc.command)) {
					Object obj2 = stack.removeLast();
					Object obj1 = stack.removeLast();
					if ((obj1 instanceof Number) && (obj2 instanceof Number))
						stack.addLast(new Boolean(((Number) obj1).floatValue() != ((Number) obj2).floatValue()));
					else if (((obj1 instanceof PsString) || (obj1 instanceof Name)) && ((obj2 instanceof PsString) || (obj2 instanceof Name)))
						stack.addLast(new Boolean(obj1.toString().compareTo(obj2.toString()) != 0));
					else stack.addLast(new Boolean(!obj1.equals(obj2)));
				}
				
				//	compare last two stack elements
				else if ("gt".equals(psc.command)) {
					Object obj2 = stack.removeLast();
					Object obj1 = stack.removeLast();
					if ((obj1 instanceof Number) && (obj2 instanceof Number))
						stack.addLast(new Boolean(((Number) obj1).floatValue() > ((Number) obj2).floatValue()));
					else if ((obj1 instanceof PsString) && (obj2 instanceof PsString))
						stack.addLast(new Boolean(((PsString) obj1).toString().compareTo(((PsString) obj2).toString()) > 0));
					else throw new IllegalArgumentException("Cannot compare " + obj1 + " and " + obj2);
				}
				else if ("ge".equals(psc.command)) {
					Object obj2 = stack.removeLast();
					Object obj1 = stack.removeLast();
					if ((obj1 instanceof Number) && (obj2 instanceof Number))
						stack.addLast(new Boolean(((Number) obj1).floatValue() >= ((Number) obj2).floatValue()));
					else if ((obj1 instanceof PsString) && (obj2 instanceof PsString))
						stack.addLast(new Boolean(((PsString) obj1).toString().compareTo(((PsString) obj2).toString()) >= 0));
					else throw new IllegalArgumentException("Cannot compare " + obj1 + " and " + obj2);
				}
				else if ("eq".equals(psc.command)) {
					Object obj2 = stack.removeLast();
					Object obj1 = stack.removeLast();
					if ((obj1 instanceof Number) && (obj2 instanceof Number))
						stack.addLast(new Boolean(((Number) obj1).floatValue() == ((Number) obj2).floatValue()));
					else if ((obj1 instanceof PsString) && (obj2 instanceof PsString))
						stack.addLast(new Boolean(((PsString) obj1).toString().compareTo(((PsString) obj2).toString()) == 0));
					else throw new IllegalArgumentException("Cannot compare " + obj1 + " and " + obj2);
				}
				else if ("le".equals(psc.command)) {
					Object obj2 = stack.removeLast();
					Object obj1 = stack.removeLast();
					if ((obj1 instanceof Number) && (obj2 instanceof Number))
						stack.addLast(new Boolean(((Number) obj1).floatValue() <= ((Number) obj2).floatValue()));
					else if ((obj1 instanceof PsString) && (obj2 instanceof PsString))
						stack.addLast(new Boolean(((PsString) obj1).toString().compareTo(((PsString) obj2).toString()) <= 0));
					else throw new IllegalArgumentException("Cannot compare " + obj1 + " and " + obj2);
				}
				else if ("lt".equals(psc.command)) {
					Object obj2 = stack.removeLast();
					Object obj1 = stack.removeLast();
					if ((obj1 instanceof Number) && (obj2 instanceof Number))
						stack.addLast(new Boolean(((Number) obj1).floatValue() < ((Number) obj2).floatValue()));
					else if ((obj1 instanceof PsString) && (obj2 instanceof PsString))
						stack.addLast(new Boolean(((PsString) obj1).toString().compareTo(((PsString) obj2).toString()) < 0));
					else throw new IllegalArgumentException("Cannot compare " + obj1 + " and " + obj2);
				}
				
				
				
				//	user defined command
				else if ((dictStack.isEmpty() ? state : ((Hashtable) dictStack.getLast())).containsKey(psc.command)) {
					Object obj = (dictStack.isEmpty() ? state : ((Hashtable) dictStack.getLast())).get(psc.command);
					System.out.println("Got " + psc.command + ": " + obj.toString());
					PsProcedure proc = ((PsProcedure) obj);
					System.out.println("Executing " + proc.toString());
					proc.execute(state, stack);
				}
				
				//	other command, yet to be implements
				else {
					if (REPORT_UNKNOWN_COMMANDS)
						throw new RuntimeException("Unknown PostScript command: " + psc.command);
					System.out.println("Other command: " + psc.command);
				}
				
				if (indent != null)
					System.out.println(indent + psc.command + " ==> " + stack);
			}
			else {
				stack.addLast(next);
				if (indent != null)
					System.out.println(indent + next + " ==> " + stack);
			}
		}
//		System.out.println("Final stack is: " + stack);
//		System.out.println("Final dict stack is: " + dictStack);
//		System.out.println("Final state is: " + state);
	}
	
	private static final boolean DEBUG_PARSE_PS = false;
	
	private static Object cropNext(PdfByteInputStream bytes) throws IOException {
		if (!bytes.skipSpaceCheckEnd())
			return null;
		
		//	skip any comment
		while (bytes.peek() == '%') {
			skipComment(bytes);
			if (!bytes.skipSpaceCheckEnd())
				return null;
		}
		
		//	hex string, dictionary, or stream parameters
		if (bytes.peek() == '<') {
			bytes.read();
			if (bytes.peek() == '<') {
				bytes.read();
				return cropHashtable(bytes);
			}
			else return cropHexString(bytes);
		}
		
		//	procedure
		else if (bytes.peek() == '{') {
			bytes.read();
			return cropProcedure(bytes);
		}
		
		//	string
		else if (bytes.peek() == '(') {
			bytes.read();
			return cropString(bytes);
		}
		
		//	array
		else if (bytes.peek() == '[') {
			bytes.read();
			return cropArray(bytes);
		}
		
		//	name
		else if (bytes.peek() == '/') {
			return cropName(bytes);
		}
		
		//	boolean, number, null, or function call
		else {
			byte[] lookahead = new byte[16]; // 16 bytes is reasonable for detecting references, as object number and generation number combined will rarely exceed 13 digits
			int l = bytes.peek(lookahead);
			if (l == -1)
				return null;
			if (DEBUG_PARSE_PS) System.out.println("Got lookahead: " + new String(lookahead));
			
			if (DEBUG_PARSE_PS) System.out.println(" --> other object");
			StringBuffer valueBuffer = new StringBuffer();
			while ((bytes.peek() > 32) && ("%()<>[]{}/#".indexOf((char) bytes.peek()) == -1))
				valueBuffer.append((char) bytes.read());
			String value = valueBuffer.toString();
			if (DEBUG_PARSE_PS) System.out.println(" --> value: " + value);
			if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
				if (DEBUG_PARSE_PS) System.out.println(" ==> boolean");
				return new Boolean(value);
			}
			else if ("null".equalsIgnoreCase(value)) {
				if (DEBUG_PARSE_PS) System.out.println(" ==> null");
				return NULL;
			}
			else if ("FontDirectory".equals(value)) {
				if (DEBUG_PARSE_PS) System.out.println(" ==> command");
				return new PsCommand(value);
			}
			else if (value.matches("[A-Za-z]+")) {
				if (DEBUG_PARSE_PS) System.out.println(" ==> command");
				return new PsCommand(value);
			}
			else if (value.matches("\\-?[0-9]++")) {
				if (DEBUG_PARSE_PS) System.out.println(" ==> integer");
				return new Integer(value);
			}
			else if (value.matches("\\-?[0-9]*+\\.[0-9]++")) {
				if (DEBUG_PARSE_PS) System.out.println(" ==> double");
				return new Double(value);
			}
			else {
				if (DEBUG_PARSE_PS) System.out.println(" ==> double");
				return new Double(value);
			}
		}
	}
	private static final Object NULL = new Object();
	
	private static void skipComment(PdfByteInputStream bytes) throws IOException {
		if (DEBUG_PARSE_PS) System.out.println("Skipping comment");
		StringBuffer comment = new StringBuffer();
		while (bytes.peek() != -1) {
			if ((bytes.peek() == '\r') || bytes.peek() == '\n')
				break;
			comment.append((char) bytes.read());
		}
		while ((bytes.peek() == '\r') || (bytes.peek() == '\n'))
			bytes.read();
		if (DEBUG_PARSE_PS) System.out.println(" --> " + comment.toString());
	}
	
	private static Name cropName(PdfByteInputStream bytes) throws IOException {
		if (DEBUG_PARSE_PS) System.out.println("Cropping name");
		if (bytes.peek() == '/')
			bytes.read();
		StringBuffer name = new StringBuffer();
		while (true) {
			if ((bytes.peek() < 33) || ("%()<>[]{}/".indexOf((char) bytes.peek()) != -1))
				break;
			if (bytes.peek() == '#') {
				bytes.read();
				int hb = bytes.read();
				int lb = bytes.read();
				if (checkHexByte(hb) && checkHexByte(lb))
					name.append((char) translateHexBytes(hb, lb));
				else throw new IOException("Invalid escaped character #" + ((char) hb) + "" + ((char) lb));
			}
			else name.append((char) bytes.read());
		}
		if (DEBUG_PARSE_PS) System.out.println(" --> " + name.toString());
		return new Name(name.toString());
	}
	
	private static boolean checkHexByte(int b) {
		if ((b <= '9') && ('0' <= b))
			return true;
		else if ((b <= 'f') && ('a' <= b))
			return true;
		else if ((b <= 'F') && ('A' <= b))
			return true;
		else return false;
	}
	private static int translateHexBytes(int hb, int lb) {
		int v = 0;
		v += translateHexByte(hb);
		v <<= 4;
		v += translateHexByte(lb);
		return v;
	}
	private static int translateHexByte(int b) {
		if (('0' <= b) && (b <= '9')) return (((int) b) - '0');
		else if (('a' <= b) && (b <= 'f')) return (((int) b) - 'a' + 10);
		else if (('A' <= b) && (b <= 'F')) return (((int) b) - 'A' + 10);
		else return 0;
	}
	
	//	TODOne decode bytes only when encoding clear
	private static PsString cropString(PdfByteInputStream bytes) throws IOException {
		if (DEBUG_PARSE_PS) System.out.println("Cropping string");
		ByteArrayOutputStream oBaos = (DEBUG_PARSE_PS ? new ByteArrayOutputStream() : null);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int open = 1;
		boolean escaped = false;
		while (bytes.peek() != -1) {
			if (escaped) {
				int peek = bytes.peek();
				if (('0' <= peek) && (peek <= '9')) {
					int oct = 0;
					if (oBaos != null) oBaos.write(bytes.peek());
					int b0 = bytes.read();
					peek = bytes.peek();
					if (('0' <= peek) && (peek <= '9')) {
						if (oBaos != null) oBaos.write(bytes.peek());
						int b1 = bytes.read();
						peek = bytes.peek();
						if (('0' <= peek) && (peek <= '9')) {
							if (oBaos != null) oBaos.write(bytes.peek());
							int b2 = bytes.read();
							oct = (((b0 - '0') * 64) + ((b1 - '0') * 8) + (b2 - '0'));
						}
						else oct = (((b0 - '0') * 8) + (b1 - '0'));
					}
					else oct = (b0 - '0');
					if (oct > 127)
						oct -= 256;
					baos.write(oct);
				}
				else if (peek == 'n') {
					baos.write('\n');
					if (oBaos != null) oBaos.write(bytes.peek());
					bytes.read();
				}
				else if (peek == 'r') {
					baos.write('\r');
					if (oBaos != null) oBaos.write(bytes.peek());
					bytes.read();
				}
				else if (peek == 't') {
					baos.write('\t');
					if (oBaos != null) oBaos.write(bytes.peek());
					bytes.read();
				}
				else if (peek == 'f') {
					baos.write('\f');
					if (oBaos != null) oBaos.write(bytes.peek());
					bytes.read();
				}
				else if (peek == 'b') {
					baos.write('\b');
					if (oBaos != null) oBaos.write(bytes.peek());
					bytes.read();
				}
				else if (peek == '\r') {
					if (oBaos != null) oBaos.write(bytes.peek());
					bytes.read();
					peek = bytes.peek();
					if (peek == '\n') {
						if (oBaos != null) oBaos.write(bytes.peek());
						bytes.read();
					}
				}
				else if (peek == '\n') {
					if (oBaos != null) oBaos.write(bytes.peek());
					bytes.read();
					peek = bytes.peek();
					if (peek == '\r') {
						if (oBaos != null) oBaos.write(bytes.peek());
						bytes.read();
					}
				}
				else {
					if (oBaos != null) oBaos.write(bytes.peek());
					baos.write(bytes.read());
				}
				escaped = false;
			}
			else if (bytes.peek() == '\\') {
				escaped = true;
				if (oBaos != null) oBaos.write(bytes.peek());
				bytes.read();
			}
			else if (bytes.peek() == '(') {
				if (oBaos != null) oBaos.write(bytes.peek());
				baos.write(bytes.read());
				open++;
			}
			else if (bytes.peek() == ')') {
				open--;
				if (open == 0) {
					bytes.read();
					break;
				}
				else {
					if (oBaos != null) oBaos.write(bytes.peek());
					baos.write(bytes.read());
				}
			}
			else {
				if (oBaos != null) oBaos.write(bytes.peek());
				baos.write(bytes.read());
			}
		}
		if (DEBUG_PARSE_PS) {
			System.out.println(" -->  " + Arrays.toString(oBaos.toByteArray()));
			System.out.println(" ==> " + Arrays.toString(baos.toByteArray()));
			System.out.println("  ANSI " + new String(baos.toByteArray()));
//			System.out.println("  UTF-16LE " + new String(baos.toByteArray(), "UTF-16LE"));
//			System.out.println("  UTF-16BE " + new String(baos.toByteArray(), "UTF-16BE"));
		}
		return new PsString(baos.toByteArray());
	}
	
	private static PsString cropHexString(PdfByteInputStream bytes) throws IOException {
		if (DEBUG_PARSE_PS) System.out.println("Cropping hex string");
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		boolean withSpace = false;
		while (bytes.peek() != -1) {
			withSpace = ((bytes.peek() < 33) || withSpace);
			bytes.skipSpace();
			if (bytes.peek() == '>') {
				bytes.read();
				break;
			}
			while (bytes.peek() == '%') {
				skipComment(bytes);
				if (!bytes.skipSpaceCheckEnd())
					throw new IOException("Broken dictionary");
			}
			baos.write(bytes.read());
		}
		return new PsString(baos.toByteArray(), true);
	}
	
	private static PsProcedure cropProcedure(PdfByteInputStream bytes) throws IOException {
		if (DEBUG_PARSE_PS) System.out.println("Cropping procedure");
//		ByteArrayOutputStream oBaos = (DEBUG_PARSE_PS ? new ByteArrayOutputStream() : null);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int open = 1;
		boolean escaped = false;
		while (bytes.peek() != -1) {
			if (escaped) {
//				int peek = bytes.peek();
//				if (('0' <= peek) && (peek <= '9')) {
//					int oct = 0;
//					if (oBaos != null) oBaos.write(bytes.peek());
//					int b0 = bytes.read();
//					peek = bytes.peek();
//					if (('0' <= peek) && (peek <= '9')) {
//						if (oBaos != null) oBaos.write(bytes.peek());
//						int b1 = bytes.read();
//						peek = bytes.peek();
//						if (('0' <= peek) && (peek <= '9')) {
//							if (oBaos != null) oBaos.write(bytes.peek());
//							int b2 = bytes.read();
//							oct = (((b0 - '0') * 64) + ((b1 - '0') * 8) + (b2 - '0'));
//						}
//						else oct = (((b0 - '0') * 8) + (b1 - '0'));
//					}
//					else oct = (b0 - '0');
//					if (oct > 127)
//						oct -= 256;
//					baos.write(oct);
//				}
//				else if (peek == 'n') {
//					baos.write('\n');
//					if (oBaos != null) oBaos.write(bytes.peek());
//					bytes.read();
//				}
//				else if (peek == 'r') {
//					baos.write('\r');
//					if (oBaos != null) oBaos.write(bytes.peek());
//					bytes.read();
//				}
//				else if (peek == 't') {
//					baos.write('\t');
//					if (oBaos != null) oBaos.write(bytes.peek());
//					bytes.read();
//				}
//				else if (peek == 'f') {
//					baos.write('\f');
//					if (oBaos != null) oBaos.write(bytes.peek());
//					bytes.read();
//				}
//				else if (peek == 'b') {
//					baos.write('\b');
//					if (oBaos != null) oBaos.write(bytes.peek());
//					bytes.read();
//				}
//				else if (peek == '\r') {
//					if (oBaos != null) oBaos.write(bytes.peek());
//					bytes.read();
//					peek = bytes.peek();
//					if (peek == '\n') {
//						if (oBaos != null) oBaos.write(bytes.peek());
//						bytes.read();
//					}
//				}
//				else if (peek == '\n') {
//					if (oBaos != null) oBaos.write(bytes.peek());
//					bytes.read();
//					peek = bytes.peek();
//					if (peek == '\r') {
//						if (oBaos != null) oBaos.write(bytes.peek());
//						bytes.read();
//					}
//				}
//				else {
//					if (oBaos != null) oBaos.write(bytes.peek());
//					baos.write(bytes.read());
//				}
//				escaped = false;
			}
//			else if (bytes.peek() == '\\') {
//				escaped = true;
//				if (oBaos != null) oBaos.write(bytes.peek());
//				bytes.read();
//			}
			else if (bytes.peek() == '{') {
//				if (oBaos != null) oBaos.write(bytes.peek());
				baos.write(bytes.read());
				open++;
			}
			else if (bytes.peek() == '}') {
				open--;
				if (open == 0) {
					bytes.read();
					break;
				}
				else {
//					if (oBaos != null) oBaos.write(bytes.peek());
					baos.write(bytes.read());
				}
			}
			else {
//				if (oBaos != null) oBaos.write(bytes.peek());
				baos.write(bytes.read());
			}
		}
		if (DEBUG_PARSE_PS) {
//			System.out.println(" -->  " + Arrays.toString(oBaos.toByteArray()));
			System.out.println(" ==> " + Arrays.toString(baos.toByteArray()));
			System.out.println("  ANSI " + new String(baos.toByteArray()));
//			System.out.println("  UTF-16LE " + new String(baos.toByteArray(), "UTF-16LE"));
//			System.out.println("  UTF-16BE " + new String(baos.toByteArray(), "UTF-16BE"));
		}
		return new PsProcedure(baos.toByteArray());
	}
	
	private static Vector cropArray(PdfByteInputStream bytes) throws IOException {
		if (DEBUG_PARSE_PS) System.out.println("Cropping array");
		Vector array = new Vector(2);
		while (bytes.peek() != -1) {
			if (!bytes.skipSpaceCheckEnd())
				break;
			while (bytes.peek() == '%') {
				skipComment(bytes);
				if (!bytes.skipSpaceCheckEnd())
					throw new IOException("Broken array");
			}
			if (bytes.peek() == ']') {
				bytes.read();
				break;
			}
			while (bytes.peek() == '%') {
				skipComment(bytes);
				if (!bytes.skipSpaceCheckEnd())
					throw new IOException("Broken array");
			}
			array.add(cropNext(bytes));
		}
		if (DEBUG_PARSE_PS) System.out.println(" --> " + array.toString());
		return array;
	}
	
	private static Hashtable cropHashtable(PdfByteInputStream bytes) throws IOException {
		if (DEBUG_PARSE_PS) System.out.println("Cropping dictionary");
		Hashtable ht = new Hashtable(2);
		while (true) {
			if (!bytes.skipSpaceCheckEnd())
				throw new IOException("Broken dictionary");
			while (bytes.peek() == '%') {
				skipComment(bytes);
				if (!bytes.skipSpaceCheckEnd())
					throw new IOException("Broken dictionary");
			}
			if (bytes.peek() == '>') {
				bytes.read();
				if (bytes.peek() == '>') {
					bytes.read();
					break;
				}
				else throw new IOException("Broken dictionary");
			}
			if (!bytes.skipSpaceCheckEnd())
				throw new IOException("Broken dictionary");
			while (bytes.peek() == '%') {
				skipComment(bytes);
				if (!bytes.skipSpaceCheckEnd())
					throw new IOException("Broken dictionary");
			}
			Name name = cropName(bytes);
			if (!bytes.skipSpaceCheckEnd())
				throw new IOException("Broken dictionary");
			while (bytes.peek() == '%') {
				skipComment(bytes);
				if (!bytes.skipSpaceCheckEnd())
					throw new IOException("Broken dictionary");
			}
			Object value = cropNext(bytes);
			ht.put(name, value);
		}
		if (DEBUG_PARSE_PS) System.out.println(" --> " + ht.toString());
		return ht;
	}
	
	/**
	 * Wrapper object for a raw, undecoded string object.
	 * 
	 * @author sautter
	 */
	public static class PsString implements CharSequence {
		
		/** the raw binary data */
		public final byte[] bytes;
		
		/** does the binary data come from a HEX with 2 bytes per output byte object? */
		public final boolean isHex;
		
		PsString(byte[] bytes) {
			this(bytes, false);
		}
		PsString(byte[] bytes, boolean isHex) {
			this.bytes = bytes;
			this.isHex = isHex;
		}
		public int hashCode() {
			return this.toString().hashCode();
		}
		public boolean equals(Object obj) {
			if (obj instanceof CharSequence) {
				CharSequence cs = ((CharSequence) obj);
				if (cs.length() != this.length())
					return false;
				for (int c = 0; c < cs.length(); c++) {
					if (this.charAt(c) != cs.charAt(c))
						return false;
				}
				return true;
			}
			return false;
		}
		public String toString() {
			StringBuffer string = new StringBuffer();
			if (this.isHex) {
				for (int b = 0; b < this.bytes.length; b += 2)
					string.append((char) this.getHex(b));
			}
			else for (int c = 0; c < this.bytes.length; c++)
				string.append((char) convertUnsigned(this.bytes[c]));
			return string.toString();
		}
		public int length() {
			return (this.isHex ? ((this.bytes.length + 1) / 2) : this.bytes.length);
		}
		public char charAt(int index) {
			if (this.isHex)
				return ((char) this.getHex(index * 2));
			else return ((char) convertUnsigned(this.bytes[index]));
		}
		public CharSequence subSequence(int start, int end) {
			if (this.isHex) {
				start *= 2;
				end *= 2;
				if (end > this.bytes.length)
					end--;
			}
			byte[] bytes = new byte[end - start];
			System.arraycopy(this.bytes, start, bytes, 0, (end - start));
			return new PsString(bytes, this.isHex);
		}
		private int getHex(int index) {
			int ch = 0;
			if (('0' <= this.bytes[index]) && (this.bytes[index] <= '9'))
				ch += (this.bytes[index] - '0');
			else if (('A' <= this.bytes[index]) && (this.bytes[index] <= 'F'))
				ch += (this.bytes[index] - 'A' + 10);
			else if (('a' <= this.bytes[index]) && (this.bytes[index] <= 'f'))
				ch += (this.bytes[index] - 'a' + 10);
			index++;
			ch <<= 4;
			if (index < this.bytes.length) {
				if (('0' <= this.bytes[index]) && (this.bytes[index] <= '9'))
					ch += (this.bytes[index] - '0');
				else if (('A' <= this.bytes[index]) && (this.bytes[index] <= 'F'))
					ch += (this.bytes[index] - 'A' + 10);
				else if (('a' <= this.bytes[index]) && (this.bytes[index] <= 'f'))
					ch += (this.bytes[index] - 'a' + 10);
			}
			return ch;
		}
		private static final int convertUnsigned(byte b) {
			return ((b < 0) ? (((int) b) + 256) : b);
		}
	}
	
	/**
	 * Wrapper object for a PostScript command.
	 * 
	 * @author sautter
	 */
	public static class PsCommand {
		public final String command;
		PsCommand(String command) {
			this.command = command;
		}
	}
	
	/**
	 * Wrapper object for a raw, undecoded procedure object or named object.
	 * 
	 * @author sautter
	 */
	public static class PsProcedure {
		public final byte[] bytes;
		PsProcedure(byte[] bytes) {
			this.bytes = bytes;
		}
		public String toString() {
			return ("{" + new String(this.bytes) + "}");
		}
		public void execute(Hashtable state, LinkedList stack) throws IOException {
			executePs(this.bytes, state, stack);
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		Hashtable state = new Hashtable();
		LinkedList stack = new LinkedList();
		executePs(testData.getBytes(), state, stack);
		executePs(testData2.getBytes(), state, stack);
	}
	
	private static String testData = "%!PS-AdobeFont-1.0: JSKAWA+RealpageTIM5 1.000\r\n" + 
		"FontDirectory/JSKAWA+RealpageTIM5 known{/JSKAWA+RealpageTIM5 findfont dup/UniqueID known{dup\r\n" + 
		"/UniqueID get 630181 eq exch/FontType get 1 eq and}{pop false}ifelse\r\n" + 
		"{save true}{false}ifelse}{false}ifelse\r\n" + 
		"17 dict begin\r\n" + 
		"/FontInfo 13 dict dup begin\r\n" + 
		"/version(1.000)readonly def\r\n" + 
		"/Notice(Catchword free font)readonly def\r\n" + 
		"/FullName(JSKAWA+RealpageTIM5)readonly def\r\n" + 
		"/FamilyName(JSKAWA+RealpageTIM5)readonly def\r\n" + 
		"/Weight(Normal)readonly def\r\n" + 
		"/isFixedPitch false def\r\n" + 
		"/ItalicAngle 0 def\r\n" + 
		"/UnderlinePosition -100 def\r\n" + 
		"/UnderlineThickness 50 def\r\n" + 
		"end readonly def\r\n" + 
		"/FontName /JSKAWA+RealpageTIM5 def\r\n" + 
		"/Encoding 256 array\r\n" + 
		"dup 0/.notdef put\r\n" + 
		"dup 1/.notdef put\r\n" + 
		"dup 2/.notdef put\r\n" + 
		"dup 3/.notdef put\r\n" + 
		"dup 4/.notdef put\r\n" + 
		"dup 5/.notdef put\r\n" + 
		"dup 6/.notdef put\r\n" + 
		"dup 7/.notdef put\r\n" + 
		"dup 8/.notdef put\r\n" + 
		"dup 9/.notdef put\r\n" + 
		"dup 10/.notdef put\r\n" + 
		"dup 11/.notdef put\r\n" + 
		"dup 12/.notdef put\r\n" + 
		"dup 13/.notdef put\r\n" + 
		"dup 14/.notdef put\r\n" + 
		"dup 15/.notdef put\r\n" + 
		"dup 16/.notdef put\r\n" + 
		"dup 17/.notdef put\r\n" + 
		"dup 18/.notdef put\r\n" + 
		"dup 19/.notdef put\r\n" + 
		"dup 20/.notdef put\r\n" + 
		"dup 21/.notdef put\r\n" + 
		"dup 22/.notdef put\r\n" + 
		"dup 23/.notdef put\r\n" + 
		"dup 24/.notdef put\r\n" + 
		"dup 25/.notdef put\r\n" + 
		"dup 26/.notdef put\r\n" + 
		"dup 27/.notdef put\r\n" + 
		"dup 28/.notdef put\r\n" + 
		"dup 29/.notdef put\r\n" + 
		"dup 30/.notdef put\r\n" + 
		"dup 31/.notdef put\r\n" + 
		"dup 32/.notdef put\r\n" + 
		"dup 33/.notdef put\r\n" + 
		"dup 34/.notdef put\r\n" + 
		"dup 35/.notdef put\r\n" + 
		"dup 36/.notdef put\r\n" + 
		"dup 37/.notdef put\r\n" + 
		"dup 38/ampersand  put\r\n" + 
		"dup 39/quoteright  put\r\n" + 
		"dup 40/parenleft  put\r\n" + 
		"dup 41/parenright  put\r\n" + 
		"dup 42/.notdef put\r\n" + 
		"dup 43/.notdef put\r\n" + 
		"dup 44/comma  put\r\n" + 
		"dup 45/hyphen  put\r\n" + 
		"dup 46/period  put\r\n" + 
		"dup 47/.notdef put\r\n" + 
		"dup 48/zero  put\r\n" + 
		"dup 49/one  put\r\n" + 
		"dup 50/two  put\r\n" + 
		"dup 51/three  put\r\n" + 
		"dup 52/four  put\r\n" + 
		"dup 53/five  put\r\n" + 
		"dup 54/six  put\r\n" + 
		"dup 55/seven  put\r\n" + 
		"dup 56/eight  put\r\n" + 
		"dup 57/nine  put\r\n" + 
		"dup 58/colon  put\r\n" + 
		"dup 59/semicolon  put\r\n" + 
		"dup 60/.notdef put\r\n" + 
		"dup 61/.notdef put\r\n" + 
		"dup 62/.notdef put\r\n" + 
		"dup 63/.notdef put\r\n" + 
		"dup 64/.notdef put\r\n" + 
		"dup 65/A  put\r\n" + 
		"dup 66/B  put\r\n" + 
		"dup 67/C  put\r\n" + 
		"dup 68/D  put\r\n" + 
		"dup 69/E  put\r\n" + 
		"dup 70/F  put\r\n" + 
		"dup 71/G  put\r\n" + 
		"dup 72/H  put\r\n" + 
		"dup 73/I  put\r\n" + 
		"dup 74/J  put\r\n" + 
		"dup 75/K  put\r\n" + 
		"dup 76/L  put\r\n" + 
		"dup 77/M  put\r\n" + 
		"dup 78/N  put\r\n" + 
		"dup 79/O  put\r\n" + 
		"dup 80/P  put\r\n" + 
		"dup 81/.notdef put\r\n" + 
		"dup 82/R  put\r\n" + 
		"dup 83/S  put\r\n" + 
		"dup 84/T  put\r\n" + 
		"dup 85/U  put\r\n" + 
		"dup 86/V  put\r\n" + 
		"dup 87/W  put\r\n" + 
		"dup 88/X  put\r\n" + 
		"dup 89/.notdef put\r\n" + 
		"dup 90/Z  put\r\n" + 
		"dup 91/.notdef put\r\n" + 
		"dup 92/.notdef put\r\n" + 
		"dup 93/.notdef put\r\n" + 
		"dup 94/.notdef put\r\n" + 
		"dup 95/.notdef put\r\n" + 
		"dup 96/grave  put\r\n" + 
		"dup 97/a  put\r\n" + 
		"dup 98/b  put\r\n" + 
		"dup 99/c  put\r\n" + 
		"dup 100/d  put\r\n" + 
		"dup 101/e  put\r\n" + 
		"dup 102/f  put\r\n" + 
		"dup 103/g  put\r\n" + 
		"dup 104/h  put\r\n" + 
		"dup 105/i  put\r\n" + 
		"dup 106/j  put\r\n" + 
		"dup 107/k   put\r\n" + 
		"dup 108/l  put\r\n" + 
		"dup 109/m  put\r\n" + 
		"dup 110/n  put\r\n" + 
		"dup 111/o  put\r\n" + 
		"dup 112/p  put\r\n" + 
		"dup 113/q  put\r\n" + 
		"dup 114/r  put\r\n" + 
		"dup 115/s  put\r\n" + 
		"dup 116/t  put\r\n" + 
		"dup 117/u   put\r\n" + 
		"dup 118/v  put\r\n" + 
		"dup 119/w  put\r\n" + 
		"dup 120/x  put\r\n" + 
		"dup 121/y  put\r\n" + 
		"dup 122/z  put\r\n" + 
		"dup 123/.notdef put\r\n" + 
		"dup 124/.notdef put\r\n" + 
		"dup 125/.notdef put\r\n" + 
		"dup 126/.notdef put\r\n" + 
		"dup 127/.notdef put\r\n" + 
		"dup 128/.notdef put\r\n" + 
		"dup 129/.notdef put\r\n" + 
		"dup 130/.notdef put\r\n" + 
		"dup 131/.notdef put\r\n" + 
		"dup 132/.notdef put\r\n" + 
		"dup 133/.notdef put\r\n" + 
		"dup 134/.notdef put\r\n" + 
		"dup 135/.notdef put\r\n" + 
		"dup 136/.notdef put\r\n" + 
		"dup 137/.notdef put\r\n" + 
		"dup 138/.notdef put\r\n" + 
		"dup 139/.notdef put\r\n" + 
		"dup 140/OE  put\r\n" + 
		"dup 141/bullet4  put\r\n" + 
		"dup 142/.notdef put\r\n" + 
		"dup 143/.notdef put\r\n" + 
		"dup 144/.notdef put\r\n" + 
		"dup 145/.notdef put\r\n" + 
		"dup 146/.notdef put\r\n" + 
		"dup 147/.notdef put\r\n" + 
		"dup 148/.notdef put\r\n" + 
		"dup 149/.notdef put\r\n" + 
		"dup 150/.notdef put\r\n" + 
		"dup 151/.notdef put\r\n" + 
		"dup 152/.notdef put\r\n" + 
		"dup 153/.notdef put\r\n" + 
		"dup 154/.notdef put\r\n" + 
		"dup 155/.notdef put\r\n" + 
		"dup 156/.notdef put\r\n" + 
		"dup 157/.notdef put\r\n" + 
		"dup 158/.notdef put\r\n" + 
		"dup 159/.notdef put\r\n" + 
		"dup 160/.notdef put\r\n" + 
		"dup 161/.notdef put\r\n" + 
		"dup 162/.notdef put\r\n" + 
		"dup 163/.notdef put\r\n" + 
		"dup 164/.notdef put\r\n" + 
		"dup 165/.notdef put\r\n" + 
		"dup 166/.notdef put\r\n" + 
		"dup 167/.notdef put\r\n" + 
		"dup 168/.notdef put\r\n" + 
		"dup 169/.notdef put\r\n" + 
		"dup 170/.notdef put\r\n" + 
		"dup 171/.notdef put\r\n" + 
		"dup 172/.notdef put\r\n" + 
		"dup 173/.notdef put\r\n" + 
		"dup 174/registered  put\r\n" + 
		"dup 175/macron  put\r\n" + 
		"dup 176/.notdef put\r\n" + 
		"dup 177/plusminus   put\r\n" + 
		"dup 178/twosuperior  put\r\n" + 
		"dup 179/.notdef put\r\n" + 
		"dup 180/.notdef put\r\n" + 
		"dup 181/.notdef put\r\n" + 
		"dup 182/.notdef put\r\n" + 
		"dup 183/.notdef put\r\n" + 
		"dup 184/.notdef put\r\n" + 
		"dup 185/.notdef put\r\n" + 
		"dup 186/.notdef put\r\n" + 
		"dup 187/.notdef put\r\n" + 
		"dup 188/.notdef put\r\n" + 
		"dup 189/.notdef put\r\n" + 
		"dup 190/.notdef put\r\n" + 
		"dup 191/.notdef put\r\n" + 
		"dup 192/.notdef put\r\n" + 
		"dup 193/.notdef put\r\n" + 
		"dup 194/Acircumflex  put\r\n" + 
		"dup 195/.notdef put\r\n" + 
		"dup 196/.notdef put\r\n" + 
		"dup 197/.notdef put\r\n" + 
		"dup 198/.notdef put\r\n" + 
		"dup 199/.notdef put\r\n" + 
		"dup 200/.notdef put\r\n" + 
		"dup 201/.notdef put\r\n" + 
		"dup 202/.notdef put\r\n" + 
		"dup 203/.notdef put\r\n" + 
		"dup 204/.notdef put\r\n" + 
		"dup 205/.notdef put\r\n" + 
		"dup 206/.notdef put\r\n" + 
		"dup 207/.notdef put\r\n" + 
		"dup 208/.notdef put\r\n" + 
		"dup 209/.notdef put\r\n" + 
		"dup 210/.notdef put\r\n" + 
		"dup 211/.notdef put\r\n" + 
		"dup 212/.notdef put\r\n" + 
		"dup 213/.notdef put\r\n" + 
		"dup 214/.notdef put\r\n" + 
		"dup 215/.notdef put\r\n" + 
		"dup 216/.notdef put\r\n" + 
		"dup 217/.notdef put\r\n" + 
		"dup 218/.notdef put\r\n" + 
		"dup 219/.notdef put\r\n" + 
		"dup 220/.notdef put\r\n" + 
		"dup 221/.notdef put\r\n" + 
		"dup 222/.notdef put\r\n" + 
		"dup 223/.notdef put\r\n" + 
		"dup 224/.notdef put\r\n" + 
		"dup 225/.notdef put\r\n" + 
		"dup 226/.notdef put\r\n" + 
		"dup 227/.notdef put\r\n" + 
		"dup 228/.notdef put\r\n" + 
		"dup 229/.notdef put\r\n" + 
		"dup 230/.notdef put\r\n" + 
		"dup 231/.notdef put\r\n" + 
		"dup 232/.notdef put\r\n" + 
		"dup 233/.notdef put\r\n" + 
		"dup 234/.notdef put\r\n" + 
		"dup 235/.notdef put\r\n" + 
		"dup 236/.notdef put\r\n" + 
		"dup 237/.notdef put\r\n" + 
		"dup 238/.notdef put\r\n" + 
		"dup 239/.notdef put\r\n" + 
		"dup 240/.notdef put\r\n" + 
		"dup 241/.notdef put\r\n" + 
		"dup 242/.notdef put\r\n" + 
		"dup 243/.notdef put\r\n" + 
		"dup 244/.notdef put\r\n" + 
		"dup 245/.notdef put\r\n" + 
		"dup 246/.notdef put\r\n" + 
		"dup 247/.notdef put\r\n" + 
		"dup 248/.notdef put\r\n" + 
		"dup 249/.notdef put\r\n" + 
		"dup 250/.notdef put\r\n" + 
		"dup 251/.notdef put\r\n" + 
		"dup 252/.notdef put\r\n" + 
		"dup 253/.notdef put\r\n" + 
		"dup 254/.notdef put\r\n" + 
		"dup 255/.notdef put\r\n" + 
		" readonly def\r\n" + 
		"/PaintType 0 def\r\n" + 
		"/FontType 1 def\r\n" + 
		"/StrokeWidth 0 def\r\n" + 
		"/FontMatrix[0.001 0 0 0.001 0 0]readonly def\r\n" + 
		"/UniqueID 630181 def\r\n" + 
		"/FontBBox{-143 -241 1006 728}readonly def\r\n" + 
		"currentdict end\r\n" + 
		"currentfile";
	private static String testData2 = "dup/Private 19 dict dup begin\r\n" + 
		"/RD{string currentfile exch readstring pop}executeonly def\r\n" + 
		"/ND{noaccess def}executeonly def\r\n" + 
		"/NP{noaccess put}executeonly def\r\n" + 
		"/BlueValues[-15.000 0.000 464.000 475.000 687.000 717.000 720.000 722.000]def\r\n" + 
		"/BlueScale 0.033333335 def\r\n" + 
		"/MinFeature{16 16}ND\r\n" + 
		"/UniqueID 630181 def\r\n" + 
		"/StdHW[18]def\r\n" + 
		"/StdVW[82]def\r\n" + 
		"/StemSnapH[18 31]def\r\n" + 
		"/StemSnapV[82 158]def\r\n" + 
		"%./ForceBoldThreshold .5 def\r\n" + 
		"/ForceBold false def\r\n" + 
		"/password 5839 def\r\n" + 
		"/Subrs 16 array\r\n" + 
		"dup 0 15 RD �1p|=-�D\\�R NP\r\n" + 
		"dup 1 9 RD �1py��Uz NP\r\n" + 
		"dup 2 9 RD �1py�Ği NP\r\n" + 
		"dup 3 5 RD �1p� NP\r\n" + 
		"dup 4 12 RD �1p~�+6�6z NP\r\n" + 
		"dup 5 5 RD �1p� NP\r\n" + 
		"dup 6 5 RD �1p� NP\r\n" + 
		"dup 7 5 RD �1p� NP\r\n" + 
		"dup 8 5 RD �1p� NP\r\n" + 
		"dup 9 5 RD �1p� NP\r\n" + 
		"dup 10 5 RD �1p� NP\r\n" + 
		"dup 11 8 RD �1py��� NP\r\n" + 
		"dup 12 9 RD �1p>~l* NP\r\n" + 
		"dup 13 9 RD �1pdK��� NP\r\n" + 
		"dup 14 9 RD �1p\n" + 
		"�f�* NP\r\n" + 
		"dup 15 9 RD �1pdK�7� NP\r\n" + 
		"ND\r\n" + 
		"2 index\r\n" + 
		"/CharStrings  78 dict dup begin\r\n" + 
		"/.notdef 9 RD �I	^\"�ϵ ND\r\n" + 
		"/ampersand 217 RD �1p^�6\\�šp*�Dc�{D'�>Écy�uq�@�Y@@9�!�\"\r\n" + 
		"k�o��ȩ\r\n" + 
		"D��*�J�R�mtwI�t/�/�Y^M����<��P/�G��/��#�4�����rnV� 7E���.\r\n" + 
		"�wu�(HaԸ�۽�rr9�����g�h��?��2�^��D;��.����WZU��p �p�\r\n" + 
		"oÛgHC0r�\r\n" + 
		"�(�1��@%�:\r\n" + 
		"ᙑ��6��xn����^� ND\r\n" + 
		"/quoteright 70 RD �1p;�u�h��&�����6���?X�?Q�����(n|4;��;���<.KݞH˱H33�b0�j���� ND\r\n" + 
		"/parenleft 64 RD �1pE��)�I]���3�o�Z��t7�I݄g�������>���Gg�}�/�[��\"�����F&��Q ND\r\n" + 
		"/parenright 63 RD �1ph����Q|�r��h.�ʜ�.���y5ުIn9����d��ŽF��������jA?�� ND\r\n" + 
		"/comma 69 RD �1p;�u�h��1�(�ҙ�*74e��}��1���Z�g�{�R=�k����?7��އ8��](=h���� ND\r\n" + 
		"/hyphen 28 RD �1pD�;.�1���D8�d��\r\n" + 
		"��8z ND\r\n" + 
		"/period 40 RD �1p)YF���H�|�i�K@�����Ȳ�>i��t��ӣ� ND\r\n" + 
		"/zero 86 RD �1pY�ʿ�?w6 �~�D*f�R�[��_�Q�qʧ��Y4K�쫓%��@?[\r\n" + 
		"�����1?\\�N���FLZM?�k\"��i�}-�� ND\r\n" + 
		"/one 68 RD �1p���j\r\n" + 
		"���	�[?�`x���?p�ص&���|��w��Pm���̜|���;��*-L]z� ?�n� ND\r\n" + 
		"/two 92 RD �1pUѺ�2���)��ey�2�ȿK�@e2U���{�`���o�F�]*�e��Ԡ�i�\r\n" + 
		"Gf Tv�j\\�������mj�ܫ�;N�Nm ND\r\n" + 
		"/three 110 RD �1p6���=es����bn)nA@)La��m�z\\ċ�kO��O*e^���&V?F ?��%��+}��w�����?u}Zݢ�1��[��ė˸F3�B,�C;�`�2H� ND\r\n" + 
		"/four 67 RD �1pi��qI�][�J,�/k�}5�?%%�bJ,�J�(9F�Ck2�<��5��֚�a�0(����? ND\r\n" + 
		"/five 90 RD �1p;�V���r�� �N�5m^�є�乶B���L������6d���C��Q�y%�P��ާAօK?����?���-�k��u� ND\r\n" + 
		"/six 120 RD �1pHUִt�Ο_�3�+��)���̩y��զ����ω�(4?u=h�OT,�=vV��q��z�Sq9�i���Ebg.����T��1����`�K��j�i�=b�}L8? ND\r\n" + 
		"/seven 50 RD �1pG'$8�'\"k�1p�F�&>9	�W�=r��YC��һ�sg�)�;�S ND\r\n" + 
		"/eight 138 RD �1p7 �gCO9(�;�\"��l.D�L���>�_���&+�\"r����F�_D�?�����{�A=��[��u�-�V��S}�}�?�:@D�;���`��c2�Ӄ��[��?g}Y-��`�F�J�L9wn�G�w2w��U�# ND\r\n" + 
		"/nine 119 RD �1pB/�Wc�r���=Y4�$^\\��.���Hç?O��K6?8,TLc_�K(�Ȍ�ߔ�EmX���Vu#u3��B3�Ћ�K�B�\r\n" + 
		"� ��g��?j^��w�?���ݶ�h%P廓� ND\r\n" + 
		"/colon 70 RD �1p)YF���H�9ʱ�'ړ!��/��l`�Vv �꿵%[|���1L�e����?|g���ZAW����P?�| ND\r\n" + 
		"/semicolon 99 RD �1p;�u�h(K�T�C��Z�����֫�Jf�R��1��hl18כ\r\n" + 
		"_��?�2nP���?YmD;�Zls�Ҭ�2E�~�:|GT)�gJ?o��润%� ND\r\n" + 
		"/A 116 RD �1pkx�7^t�x]O� �1�,�j���7�`��Ц	b,�y�o-r�hDq��,�\"/��=��(C���1�V����?Hd���v���\r\n" + 
		"?�0�g�YIby6�I�w�Ȯ�f��e��h ND\r\n" + 
		"/B 139 RD �1pF��S)��҄��A��l���>U�%#�DF������!]�1ij?j�E��:9)��| }�N��������j�\\�wՇ\r\n" + 
		"F�5�G���[�N�}�?#,���/��8�nL��ǎU���Q;�b耹h ND\r\n" + 
		"/C 109 RD �1pG&���dRNj�L��=g�5��A2��@K_\\|��}�t��n� .I��?3F�46��Og��{3�[?�J^?���M�4�t�z@���f�K�sd�#�qi� ND\r\n" + 
		"/D 102 RD �1pY��>��X9�&\"�6O��H�ɀ+���UHݦ�7.tJ'��;��Nr�?��,~�)Ax��������ֳ�NQ���(Y#XJ�o��%[>`��� ND\r\n" + 
		"/E 121 RD �1p]e�?��J�X�6p?7��t�;g���[�xw����V�a?yU�;=�nS�#��:D@-k�|Wѣ�\r\n" + 
		")|�z�!�dLD0=��V��(��ʹs���o��Sa�ŧ>w�~yE��M ND\r\n" + 
		"/F 110 RD �1pD���N�.�? ��L6c��7 R gl^�Z�����s���:����x����ZZ�j�%��\"2���� 5����?�?#?]��s����HB\\�N�V?pswp ND\r\n" + 
		"/G 141 RD �1pD�z�l�?��w^�]�=��f���|	��,W��Z��˫'\\���\r\n" + 
		"�7�H��f����%.Pl�wg)����?\r\n" + 
		"��#	�ҋ��A����]�öQA���)i�ڡ�m��ɉ���\\U�a�%�g�j��0c�y�[ ND\r\n" + 
		"/H 131 RD �1p\\7�!zoZ�T�?f* �\\f6�]%�	eG�_����+�k��\"��͒aY'�Rȥ��?�}g�V�1J����?o ��DHr,zJ���\"����u��T�?J�	f �����l)��	��o ND\r\n" + 
		"/I 63 RD �1p\\6z�r��'�;:�0��t�|#/Qy_b�[�Q#G������Kԇ L��&ǎ��?� ND\r\n" + 
		"/J 78 RD �1pStJ.�������g�g(�QL�ɱ�?��i�*�[�;������t?�G����`�JP��9�D��q;�?\"iͅ�vn ND\r\n" + 
		"/K 146 RD �1pA�;!��y��_\\�\r\n" + 
		"t�x�e�2�T&�tX��Z(�pU���ھ`��oO�}ˣs�*�?�?Ŀ�.\\R��j�\"Z�?�<����7�I�AZ���\"�I��d	Fj�������!?��`ն<���H��O?��z��Me� ND\r\n" + 
		"/L 82 RD �1pG&����?�qsd�@F�ř�2�ZWs�+�ɓ5��?���F-��a�x���Ǌc�a�9��f?�\r\n" + 
		")��CHY����F44 ND\r\n" + 
		"/M 128 RD �1pD�?�k���6��s�a�s:nRr��K2Q��C��Z�w�EobjeK��/��\"�m@^?�p�MA�	��qFgV���ZY���	�*���0\r\n" + 
		"��$������;���\"[��_��� ND\r\n" + 
		"/N 103 RD �1pj���?Vn_;�/�G�1sY$�� �)ah����s�>�I3>%���S�.S�h��uG��u��9�O.���ި��1��l8:7?�'a/u�	E�$z�w5 ND\r\n" + 
		"/O 101 RD �1pB.�\r\n" + 
		"\r\n" + 
		"�G8T������6�?<�j:����+����)W�*q��t~�KE|U�üPǻ2�'��Ļ�7`ċ�D�XbBדO����@���?�~ ND\r\n" + 
		"/P 116 RD �1pV��&�޳��S?�w?�un�c\\�/�����+�s�b��?VPyb!�_ dpY>Y�O�\r\n" + 
		"�*{�� �� b��ie(W~L���{!�\r\n" + 
		"�%��<���ג}g�w(�Vp� ND\r\n" + 
		"/R 128 RD �1p_���X��N�9(4�Kb�-v���ޣf�Z;��F\"�!����'G�q����q��C�^?�u��L����6#ZJ��ʸFU^\\�ѻ���������\"���B��/�b��+B�?tn������ ND\r\n" + 
		"/S 150 RD �1p7 /�\\V���G3#?���D9�wr� *B�,dro�����\r\n" + 
		"�[Ì���թzr\r\n" + 
		"̢B�s�̕�?���$�9gߗ9Q�{}�.7&�]s��D���x*I�����(A(�D�f���G�*��*�X�;�$>��`q�� ND\r\n" + 
		"/T 83 RD �1pV��+���?-������r��ܩx����X�zO������F.�D�t&��?�iH���:���AUJUN'��\r\n" + 
		"%� ND\r\n" + 
		"/U 105 RD �1pY��>��c �/�i����:k���?Gs����U��PWkthU?D>݇\r\n" + 
		"�������.w�+ּ�E}�������Dm~���4+������nO�Y�`�T� ND\r\n" + 
		"/V 99 RD �1pe��~`�I,��tRH(?��O�U��:\\��p�� a�Xdִ��l����O (Ē?�zwb�Ω�U�?m�Ӻ��8�.�NhR; ND\r\n" + 
		"/W 156 RD �1p`�=R���,3H%��G��=m*x�]��Q�������9%;�p	%�¥�nH. v���B��-���9�?+��5l�/�ݑ����b���Щ�h\\�&#���\\f?��N�7$��N0$������>�����q�����Q=��b�CsX ND\r\n" + 
		"/X 171 RD �1pj�S��.�F>�TZ7�ɤ���n������-?��ut�m�dm�7���p\r\n" + 
		"�����L.�Mr��M�kDt��P����?��S�y��/ ԋ�{E�����M-&%�|��7�?|]?��%ڀ�ˈ	�%:j3�%`vw���8ܿ~i%_��V���4� ND\r\n" + 
		"/Z 71 RD �1pRD��(������[�-���(�j�4���s�#qc�w�����$q�C�d����(.=�9޺Pd�� ND\r\n" + 
		"/grave 67 RD �1p;�u�.�]���(T@Ph�k����?� >?�v��F/j���5Y\\�6渄�U*H��3� ND\r\n" + 
		"/a 155 RD �1pA�IE��k\r\n" + 
		",��{,@�l�,�#��P���)�v�8r��>��?���¢�YC�TM��4��+�ߺ�����H���S�Xy�?��j�BB�;�]���c�_�ba�\r\n" + 
		"��z�?0�?$�J�B?&h�j��	�/�[|�ud��ӭT���b�Z ND\r\n" + 
		"/b 128 RD �1pRE��O�@\r\n" + 
		"�MV�.�P��$�s�\"��?���z��1��a,�h�8WͰ |=$��:�i&�>�g�Z�ZFK)8j�Fn����l5��[���ƭ������?��o4�]��d�����Y�A|� ND\r\n" + 
		"/c 96 RD �1pQ��i�[`q?�w�H4J�(C��S�&�W�jb�?ٽҖW���]>E�6������@������aJΦt�u�.K)����?� ND\r\n" + 
		"/d 155 RD �1pY����rcv?��^d��%����=X �N�E9/9���f�� <R�4�?��Zp�1�?ʊ���ʑ����U|�?1���&�p�W9�Ӭ LK[?��g�P!k����|?,,U�~Ue٣�^�c��ӪCt��#���+������$��ʔH|(�8 ND\r\n" + 
		"/e 99 RD �1pU�x�-l?ܺQ'�O�l����@C1�H$�{$��%���RA@Z��:T?��?ܔ�����1��q1��	?�@�S��~.OV�����3/���(k�� ND\r\n" + 
		"/f 93 RD �1pQ}AA?�n!�Z5G�dT�X\r\n" + 
		" #EEޚ����GF6�~�%���?����Y�/�P�E��%�\r\n" + 
		"�'b�a�� /A��\\㻯&Ƌ����,JV�E ND\r\n" + 
		"/g 211 RD �1pY�ʿ��u�kl�XD9?3NN�Xl�J�#�sm/��1ݭ�Pa%*�c%����?&^�_��ð\r\n" + 
		"\r\n" + 
		"\\�W���n,�C�u�n��sj?�$,��o\"-4E�./\r\n" + 
		"p�ן{\r\n" + 
		"�f����g�_` M�Y���v�bꠀa⡌b����47\\�a9\"S�2%�~>\r\n" + 
		"m\"�8�|��2Q�s�s&sRd�C?��Q`-���-2���y ND\r\n" + 
		"/h 134 RD �1plLo��l..	m���6Q?e:(�J$�ZRC���^X��bV}�dB�JX �?�vF�$�S�\r\n" + 
		"�B��(�3�.�N?�*�y�H3���>~�[��?q�]ّߤxټ�s�G�F�;��&�[Z�\\�Q[�?����Z ND\r\n" + 
		"/i 103 RD �1pP֕V~Qm���?���gV��g�_��&�S:�U?L?�I���9�k�����>	�K9ټ=�_.r?�uj�1�z���۳��b��y�:��rG]P�Y� ND\r\n" + 
		"/j 115 RD �1p�~���a����qJ���:Os��M��K-]��'��̭�\"�	�c�U�I�{5�W-���(�yc�\r\n" + 
		"j,�5ov���A�:+���l��5��UOۇ�W/\\f+�{?��O�% ND\r\n" + 
		"/k  153 RD �1pW>b�Ҿ�O&�����qXv���T��o�¸�h0����NIXĥ���,��Ga�?1$&Z�+7$����U?7Lu2XHw���������Q�\r\n" + 
		"�}�J��?Y�֔1����Q �Ie��P�y�m�hĉ[��e�\"� ND\r\n" + 
		"/l 74 RD �1pP֕W�s\\��7+yL�����O��i\\�jBO@��e�?2�m�w7\r\n" + 
		"[I��h&yE1��ar��*�SW�1�0S ND\r\n" + 
		"/m 188 RD �1pa/9�.ۈo���2���V����ّ���H��$9|�X\"�?��'r��v\"\r\n" + 
		"Uw# �u���rv@	J���ױ�^9p����L뵟i /�1��g�P*@�\")P���d������˿�#��<���l���-��n2�m�� aS�|XpÃ��:�[701��C=-��Yw�D`?5/��.��mĢ�ގ ND\r\n" + 
		"/n 135 RD �1po�:<g:i�vxt�+M�M����C���t��Z!�Z%L��J�uXu�H��je��gE�\r\n" + 
		"�+��?�B��>�A�t;��4�����4h��^�d�;��ޤ$�.=�2�+���w���S1B�Y�*[�/� ND\r\n" + 
		"/o 90 RD �1pJ�,;{ͮ*]P:�f�s�ŔB4��,7�O���m�]G|���)���Oo8�:s��q�M?F�cw츞�?Fu�e��� ND\r\n" + 
		"/p 145 RD �1pn��	�?Q7vX�0�AD�n)�F�4�ȫ�Լ�M~d�֌�?�q�OUe���;�GGܖ��˺�Y{��B���D�<Ot�ML��m쉲|� /�zQJ)x��?	%,ܠW��闎�P����uhǋ�j��?��4���� ND\r\n" + 
		"/q 125 RD �1pB/�v]�?(�!�\r\n" + 
		"�]�W���Z�\"�]��#D���Eh��^3����Ev�l�,�V��1�8�$�_���(z���QWgw%���?��Vr=U�0��fb�*���)?�,�#YM�%��n�?�OC2 ND\r\n" + 
		"/r 111 RD �1pT��H�^,��L�%f]������5�G���A��oN��E4Kx�K{Ӭf���N��1B#'�c��P���6a��&N�1�����]?|�$6�L���3M'��E p�4 ND\r\n" + 
		"/s 145 RD �1pN|zqBUu�sX�gw�vbD�Y=�4ĺ����g3��r&�Z��������D�9SU\r\n" + 
		"l��wn�x�H)9R��/���iňþG��y���1c�$�酅\r\n" + 
		"h)�3��?Z�|K<]�K�<���16?6�3ܶ�?����Ș ND\r\n" + 
		"/t 76 RD �1pZƶU|��ʈ�/�[�����S��?t�t(�ZwA�T��U�x����;I���<�Ȗ�D$��r���? ND\r\n" + 
		"/u  119 RD �1po�:<O�s�w&����t�\r\n" + 
		"~3��k�a���L�|��Lr��$�����L& Z�J�=j��?��o���1!���Io#p�s{U�r%\"�?֭m��`?��:�4N��?�<Y}��O���� ND\r\n" + 
		"/v 97 RD �1pj�}�rJ6���x����Π ?	ɥ��\"�'��m�4F�?@j�Y��1'0to���h�?�)d� �4�?ó�-�?�b��)�'��}y� ND\r\n" + 
		"/w 150 RD �1pe��~a�w:�rT?� =��\r\n" + 
		"cj����w���W[IZ8S��\r\n" + 
		"ϽoS�Da�_l9��C#�Sl�\r\n" + 
		"?��Q_�`&l�e�jskF��)�����cy�3�ج/D	�%t믉��^XI�]sw�6)b�$8V%�y���H���D;z� ND\r\n" + 
		"/x 160 RD �1pn�?�����o������c|�3֨�x����~��/I?m+]E�����,׼�MA�_�uv�������^�~? *#\r\n" + 
		"���Qψ�;�����\"+��\r\n" + 
		"����}�nZ�(���i�{@�8�ϙwS�{���kg�����n�4�	��ĵ�� ND\r\n" + 
		"/y 129 RD �1pi��qIТ�=���+JQ�#�\\���c�ʷ�u	?�ߥ?�~i' �,N�u�<�~d�6�e�?�d@��*wP�B�RN��Gq�S��QΥ\"բ��+\\TL5��'�H�����-�2T�/���w�P�-�*;� ND\r\n" + 
		"/z 71 RD �1pkk��Q�H齖�20-�\r\n" + 
		"�k���͗�v�?�Ҝ��%�d^��#�D6�>���?z\"�\r\n" + 
		"�H8����>�� ND\r\n" + 
		"/OE 177 RD �1p��݌�O��T�F��(��w�Q�6��c��q����}Up����?����p���t��4Ydbή����Y��$ڜ8t��z?UkD4�{%�$�Ѱl�������Ӭ�rp���ƺMn��y�[lNC�=���??��I��Rۺ��\\���.o�_ �8�y� ND\r\n" + 
		"/bullet4 213 RD �1p?�!??1�>��מ��+=J0�[$�2exA�n��I[����8W;�^B[�#W|�mz�y���3�?q!�cى:�?r�����?BG���>����w�2��ٞ8\r\n" + 
		"�Z�F����\r\n" + 
		"\r\n" + 
		"�>���Y?\"Jk2��6_2 ��x�L� �Sňb)\"�b?T��n��h(J�rk�hO�h�T'H�M��q���?����l���Aނ � ND\r\n" + 
		"/registered 138 RD �1po�:<g?ake%�KX���$mʲ�S��a�`�x2=G|�.�+LO�N��̛�D�éh����7D��.�ڣ�\r\n" + 
		"�����==L��H�H��n�/��%9w�qh�	X��튴��`X5��՛|Vg���Ǣ�� ND\r\n" + 
		"/macron 136 RD �1plLo��?J�	<��r��_�b�_7�.-�\"g�ؗ���7��篭Χ�Wf��Sn>�[���%̇�����I����x?$5�|���t� $Pq��ۓV�d5�?�8d*���?���xn���\"�/� ND\r\n" + 
		"/plusminus  24 RD �1pwk�������\\�?2/@�?m�� ND\r\n" + 
		"/twosuperior 133 RD �1pMMg�+�㕿��7�U	/F.��P�漬!?=6�Vqw���\"�_x������xm�Tij��'Ga�?\\���J�˛)g�dO�,��T�q����ȑ�Α0>vܞ1�ǹ|E'×�D�	�� ND\r\n" + 
		"/Acircumflex 27 RD �1p�vP �Ȳ�Ց���������� ND\r\n" + 
		"end end readonly put put\r\n" + 
		"dup/FontName get exch definefont pop\r\n" + 
		"mark currentfile closefile";
}