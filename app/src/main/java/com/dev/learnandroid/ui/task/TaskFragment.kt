package com.dev.learnandroid.ui.task

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dev.learnandroid.R
import com.dev.learnandroid.data.local.SortOrder
import com.dev.learnandroid.data.local.Task
import com.dev.learnandroid.databinding.FragmentTaskBinding
import com.dev.learnandroid.ui.TASK_ADDED
import com.dev.learnandroid.util.onQueryTextChange
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TaskFragment : Fragment(R.layout.fragment_task), TaskAdapter.OnItemClickListener {

    private val viewModel: TaskViewModel by viewModels()


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = FragmentTaskBinding.bind(view)

        val taskAdapter = TaskAdapter(this)

        binding.apply {
            taskList.apply {
                adapter = taskAdapter
                layoutManager = LinearLayoutManager(requireContext())
                setHasFixedSize(true)
            }

            ItemTouchHelper(object :
                ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    return false
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val task = taskAdapter.currentList[viewHolder.adapterPosition]
                    viewModel.onTaskSwiped(task)
                }
            }).attachToRecyclerView(taskList)

            taskFab.setOnClickListener { viewModel.addOnAddTaskClick() }

        }

        setFragmentResultListener("add_edit_task") { _, bundle ->
            viewModel.onAddEditResult(bundle.getInt("result"))
        }

        viewModel.taskList.observe(viewLifecycleOwner) { tasks ->
            taskAdapter.submitList(tasks)
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.taskEvent.collect { event ->
                when (event) {
                    is TaskViewModel.TaskEvent.ShouldUndoDeleteTaskMessage -> {
                        Snackbar.make(requireView(), "Task deleted", Snackbar.LENGTH_LONG)
                            .setAction("UNDO") {
                                viewModel.onUndoDeleteClick(event.task)
                            }.show()
                    }
                    is TaskViewModel.TaskEvent.NavigateToAddTaskFragment -> {
                        val action =
                            TaskFragmentDirections.actionTaskFragmentToAddEditTaskFragment(title = "New Task")
                        findNavController().navigate(action)
                    }
                    is TaskViewModel.TaskEvent.NavigateToEditTaskFragment -> {
                        val action =
                            TaskFragmentDirections.actionTaskFragmentToAddEditTaskFragment(
                                event.task,
                                title = "Edit Task"
                            )
                        findNavController().navigate(action)
                    }
                    is TaskViewModel.TaskEvent.ShowAddTaskOperationMessage -> Snackbar.make(
                        requireView(),
                        "Task added",
                        Snackbar.LENGTH_LONG
                    ).show()
                    is TaskViewModel.TaskEvent.ShowEditTaskOperationMessage -> Snackbar.make(
                        requireView(),
                        "Task edited",
                        Snackbar.LENGTH_LONG
                    ).show()
                    TaskViewModel.TaskEvent.NavigateToDeleteAllCheckedTask -> {
                        val action =
                            TaskFragmentDirections.actionGlobalDeleteCheckedTaskDialogFragment()
                        findNavController().navigate(action)
                    }
                }
            }
        }

        setHasOptionsMenu(true)
    }

    override fun onItemClicked(task: Task) {
        viewModel.onTaskSelected(task)
    }

    override fun onCheckboxClicked(task: Task, isChecked: Boolean) {
        viewModel.onTaskCheckedChange(task, isChecked)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_task, menu)

        val searchItem = menu.findItem(R.id.menu_task_search)
        val searchView = searchItem.actionView as androidx.appcompat.widget.SearchView

        searchView.onQueryTextChange { query ->
            viewModel.searchQuery.value = query
        }

        viewLifecycleOwner.lifecycleScope.launch {
            menu.findItem(R.id.menu_task_hide_complete_task).isChecked =
                viewModel.preferenceFlow.first().hideCompleted
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_task_sort_name -> {
                viewModel.onSortOrderSelected(SortOrder.BY_NAME)
                true
            }
            R.id.menu_task_sort_date -> {
                viewModel.onSortOrderSelected(SortOrder.BY_DATE)
                true
            }
            R.id.menu_task_hide_complete_task -> {
                item.isChecked = !item.isChecked
                viewModel.onHideCompleteSelected(item.isChecked)
                true
            }
            R.id.menu_task_delete_all_completed_task -> {
                viewModel.onDeleteAllCheckTaskClick()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}