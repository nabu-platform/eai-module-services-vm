/*
* Copyright (C) 2016 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.eai.module.services.vm.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Entry;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.api.StepGroup;
import be.nabu.libs.services.vm.api.VMService;
import be.nabu.libs.services.vm.step.Invoke;
import be.nabu.libs.services.vm.step.Link;
import be.nabu.libs.services.vm.step.Map;
import be.nabu.libs.services.vm.step.Sequence;

public class VMServiceUtils {
	
	public static void renumber(VMService service) {
		Sequence root = service.getRoot();
		renumber(root, 1);
	}
	
	public static void renumber(Step step) {
		// first we go to the root
		while (step.getParent() != null) {
			step = step.getParent();
		}
		renumber((StepGroup) step, 1);
	}
	
	private static int renumber(StepGroup group, int counter) {
		group.setLineNumber(counter++);
		// we don't want to recurse map steps, they are not shown in the overview
		if (group.getChildren() != null && !(group instanceof Map)) {
			for (Step child : group.getChildren()) {
				if (child instanceof Link) {
					continue;
				}
				else if (child instanceof StepGroup) {
					counter = renumber((StepGroup) child, counter);
				}
				else {
					child.setLineNumber(counter++);
				}
			}
		}
		return counter;
	}
	
	public static String templateServiceComment(Invoke invoke) {
		Entry entry = EAIResourceRepository.getInstance().getEntry(invoke.getServiceId());
		if (entry != null && entry.isNode()) {
			// a comment is meant for developers
			String comment = entry.getNode().getComment();
			// a summary is meant for business users but still relevant!
			if (comment == null || comment.trim().isEmpty()) {
				comment = entry.getNode().getSummary();
			}
			if (comment != null && !comment.trim().isEmpty()) {
				Pattern pattern = Pattern.compile("\\{[^}]+\\}");
				Matcher matcher = pattern.matcher(comment);
				while (matcher.find()) {
					String match = matcher.group();
					// the "default" value is added after a pipe
					// {<nameofinput>|default}
					// if there is no default, the name of the input is taken
					String value = match.trim();
					// remove the curly braces
					value = value.substring(1, value.length() - 1);
					String[] split = value.split("[\\s]*\\|[\\s]*");
					String templateValue = split.length == 1 ? split[0] : split[1];
					
					// the field we want to match
					// {name | a component} -> here the field is "name", the default template value is "a component"
					// {name ? component $name | a component} -> here we want to apply a template if a particular field is present
					int indexOf = split[0].indexOf('?');
					String fieldToMatch = indexOf > 0 ? split[0].substring(0, indexOf) : split[0];
					String fieldTemplate = indexOf > 0 ? split[0].substring(indexOf + 1) : "$" + fieldToMatch;
					fieldToMatch = fieldToMatch.trim();
					fieldTemplate = fieldTemplate.trim();
					templateValue = templateValue.trim();
					
					for (Step child : invoke.getChildren()) {
						if (child instanceof Link) {
							// remove queries, they don't count
							// the "input" is not included in the to
							String to = ((Link) child).getTo().replaceAll("\\[[^\\]]+\\]", "");
							String from = ((Link) child).getFrom();
							// if we found what we are mapping
							if (to.equals(fieldToMatch)) {
								// if it is a fixed value, we just add that
								if (((Link) child).isFixedValue()) {
									// we don't want very long fixed values in here, nor multilines
									from = from.replace("\\n", " ");
									if (from.length() > 32) {
										from = from.substring(0, 32) + "...";
									}
									templateValue = "\"" + from + "\"";
								}
								else {
									// we want the last word, this is the name of the ultimate variable
									// we also decamelify the thing
									// also remove queries
									templateValue = from.replaceAll("\\[[^\\]]+\\]", "").replaceAll("^.*/([^/]+)$", "$1").replaceAll("([A-Z]+)", " $1").toLowerCase();
								}
								templateValue = fieldTemplate.replace("$" + fieldToMatch, templateValue);
							}
						}
					}
					comment = comment.replace(match, templateValue);
				}
				return comment;
			}
		}
		return null;
	}
}
