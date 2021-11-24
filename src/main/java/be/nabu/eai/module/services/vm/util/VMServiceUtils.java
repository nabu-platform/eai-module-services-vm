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
		if (entry != null && entry.isNode() && entry.getNode().getComment() != null) {
			String comment = entry.getNode().getComment();
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
				for (Step child : invoke.getChildren()) {
					if (child instanceof Link) {
						// remove queries, they don't count
						// the "input" is not included in the to
						String to = ((Link) child).getTo().replaceAll("\\[[^\\]]+\\]", "");
						String from = ((Link) child).getFrom();
						// if we found what we are mapping
						if (to.equals(split[0])) {
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
						}
					}
				}
				comment = comment.replace(match, templateValue);
			}
			return comment;
		}
		return null;
	}
}
